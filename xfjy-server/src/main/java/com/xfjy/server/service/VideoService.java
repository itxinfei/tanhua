package com.xfjy.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import com.xfjy.server.config.MinioConfig;
import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.api.VideoApi;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.pojo.Video;
import com.xfjy.service.vo.PageInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.server.pojo.User;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.server.vo.PageResult;
import com.xfjy.server.vo.PicUploadResult;
import com.xfjy.server.vo.VideoVo;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class VideoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoService.class);

    @Autowired
    private VideoApi videoApi;

    @Autowired
    private PicUploadService picUploadService;

    @Autowired
    private UserInfoApi userInfoApi;

    @Autowired(required = false)
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    @Autowired
    private QuanZiApi quanZiApi;


    public String saveVideo(MultipartFile picFile, MultipartFile videoFile) {

        User user = UserThreadLocal.get();

        Video video = new Video();
        video.setUserId(user.getId());
        video.setSeeType(1);

        try {
            // MinIO 未配置（凭证环境变量未注入）时快速失败
            if (this.minioClient == null) {
                LOGGER.error("MinIO 未配置，无法上传视频，请检查 MINIO_ACCESS_KEY/MINIO_SECRET_KEY 环境变量");
                return null;
            }

            // 上传图片
            PicUploadResult picUploadResult = this.picUploadService.upload(picFile);
            video.setPicUrl(picUploadResult.getName());

            //上传视频到 MinIO
            String videoFilePath = getVideoFilePath(videoFile.getOriginalFilename());
            try (java.io.InputStream in = new java.io.ByteArrayInputStream(videoFile.getBytes())) {
                if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioConfig.getBucketName()).build())) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucketName()).build());
                }
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(videoFilePath)
                        .stream(in, videoFile.getSize(), -1)
                        .contentType(videoFile.getContentType())
                        .build());
            }
            video.setVideoUrl(minioConfig.getUrlPrefix() + videoFilePath);

            return this.videoApi.saveVideo(video);
        } catch (Exception e) {
            LOGGER.error("视频处理异常", e);
        }

        return null;
    }

    public PageResult queryVideoList(Integer page, Integer pageSize) {
        User user = UserThreadLocal.get();

        PageResult pageResult = new PageResult();
        pageResult.setPage(page);
        pageResult.setPagesize(pageSize);
        pageResult.setPages(0);
        pageResult.setCounts(0);

        PageInfo<Video> pageInfo = null;

        //先从Redis进行命中，如果命中则返回推荐列表，如果未命中查询默认列表
        String redisValue = this.redisTemplate.opsForValue().get("QUANZI_VIDEO_RECOMMEND_" + user.getId());
        if (StringUtils.isNotEmpty(redisValue)) {
            String[] vids = StringUtils.split(redisValue, ',');
            int startIndex = (page - 1) * pageSize;
            if (startIndex < vids.length) {
                int endIndex = startIndex + pageSize - 1;
                if (endIndex >= vids.length) {
                    endIndex = vids.length - 1;
                }

                List<Long> vidList = new ArrayList<>();
                for (int i = startIndex; i <= endIndex; i++) {
                    vidList.add(Long.valueOf(vids[i]));
                }

                List<Video> videoList = this.videoApi.queryVideoListByPids(vidList);
                pageInfo = new PageInfo<>();
                pageInfo.setRecords(videoList);
            }
        }

        if(null == pageInfo){
            pageInfo = this.videoApi.queryVideoList(page, pageSize);
        }

        List<Video> records = pageInfo.getRecords();
        List<VideoVo> videoVoList = this.assembleVideoVoList(records, user.getId());
        pageResult.setItems(videoVoList);

        return pageResult;
    }

    /**
     * 将 Mongo Video 列表组装为前端 VideoVo（统一填充评论数、关注/点赞态、作者信息）
     */
    private List<VideoVo> assembleVideoVoList(List<Video> records, Long userId) {
        List<VideoVo> videoVoList = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();

        // 批量查询评论数（type=2 表示评论，一次远程调用替代循环内逐条调用，消除 N+1）
        List<String> videoIds = new ArrayList<>();
        for (Video record : records) {
            videoIds.add(record.getId().toHexString());
        }
        Map<String, Long> commentCountMap;
        try {
            commentCountMap = this.quanZiApi.queryCommentCountBatch(videoIds, 2);
            if (commentCountMap == null) {
                commentCountMap = Collections.emptyMap();
            }
        } catch (Exception e) {
            LOGGER.warn("批量查询视频评论数失败，评论数将显示为0", e);
            commentCountMap = Collections.emptyMap();
        }

        for (Video record : records) {
            VideoVo videoVo = new VideoVo();

            videoVo.setUserId(record.getUserId());
            videoVo.setCover(record.getPicUrl());
            videoVo.setVideoUrl(record.getVideoUrl());
            videoVo.setId(record.getId().toHexString());
            // 签名：取用户标签作为个性签名（UserInfo 无独立签名字段，tags 为最接近的真实数据）
            videoVo.setSignature(null);

            // 评论数（来自上方批量查询）
            Long commentCount = commentCountMap.get(videoVo.getId());
            videoVo.setCommentCount(commentCount == null ? 0 : commentCount.intValue());

            // 是否关注：查询 Redis 中已记录的关注关系
            String focusKey = "VIDEO_FOLLOW_USER_" + userId + "_" + record.getUserId();
            videoVo.setHasFocus(this.redisTemplate.hasKey(focusKey) ? 1 : 0);

            String likeUserCommentKey = "QUANZI_COMMENT_LIKE_USER_" + userId + "_" + videoVo.getId();
            videoVo.setHasLiked(this.redisTemplate.hasKey(likeUserCommentKey) ? 1 : 0); //是否点赞

            String likeCommentKey = "QUANZI_COMMENT_LIKE_" + videoVo.getId();
            String value = this.redisTemplate.opsForValue().get(likeCommentKey);
            if(StringUtils.isNotEmpty(value)){
                videoVo.setLikeCount(Integer.valueOf(value)); //点赞数
            }else{
                videoVo.setLikeCount(0); //点赞数
            }

            if (!userIds.contains(record.getUserId())) {
                userIds.add(record.getUserId());
            }

            videoVoList.add(videoVo);
        }


        UserInfoQueryParam queryParam = new UserInfoQueryParam();
        queryParam.setUserIds(userIds);
        List<UserInfo> userInfos = this.userInfoApi.queryUserInfoList(queryParam);
        for (VideoVo videoVo : videoVoList) {
            for (UserInfo userInfo : userInfos) {
                if (videoVo.getUserId().longValue() == userInfo.getUserId().longValue()) {

                    videoVo.setNickname(userInfo.getNickName());
                    videoVo.setAvatar(userInfo.getLogo());
                    // 签名：取用户标签，无标签时给默认文案
                    String tags = userInfo.getTags();
                    videoVo.setSignature(StringUtils.isNotEmpty(tags) ? tags : "这个人很懒，什么都没写");

                    break;
                }
            }

        }

        return videoVoList;
    }

    /**
     * 查询「关注」流：当前用户关注的人发布的视频（分页）
     */
    public PageResult queryFollowVideoList(Integer page, Integer pageSize) {
        User user = UserThreadLocal.get();

        PageResult pageResult = new PageResult();
        pageResult.setPage(page);
        pageResult.setPagesize(pageSize);
        pageResult.setPages(0);
        pageResult.setCounts(0);

        List<Long> followIds = this.videoApi.queryFollowUserIds(user.getId());
        if (followIds == null || followIds.isEmpty()) {
            pageResult.setItems(new ArrayList<>());
            return pageResult;
        }

        PageInfo<Video> pageInfo = this.videoApi.queryVideoListByUserIds(followIds, page, pageSize);
        List<Video> records = pageInfo.getRecords();
        if (records == null) {
            records = new ArrayList<>();
        }
        List<VideoVo> videoVoList = this.assembleVideoVoList(records, user.getId());
        pageResult.setItems(videoVoList);

        return pageResult;
    }

    /**
     * 关注用户
     *
     * @param userId
     * @return
     */
    public Boolean followUser(Long userId) {
        User user = UserThreadLocal.get();
        this.videoApi.followUser(user.getId(), userId);

        //记录已关注
        String followUserKey = "VIDEO_FOLLOW_USER_" + user.getId() + "_" + userId;
        this.redisTemplate.opsForValue().set(followUserKey, "1");

        return true;
    }

    /**
     * 取消关注
     *
     * @param userId
     * @return
     */
    public Boolean disFollowUser(Long userId) {
        User user = UserThreadLocal.get();
        this.videoApi.disFollowUser(user.getId(), userId);

        String followUserKey = "VIDEO_FOLLOW_USER_" + user.getId() + "_" + userId;
        this.redisTemplate.delete(followUserKey);

        return true;
    }

    /**
     * 记录视频操作评分事件（type：2-点赞，3-取消点赞，4-评论），
     * 由视频服务内部发送 xfjy-video 消息
     */
    public void recordVideoMsg(String videoId, Integer type) {
        User user = UserThreadLocal.get();
        this.videoApi.recordVideoMsg(user.getId(), videoId, type);
    }

    private String getVideoFilePath(String sourceFileName) {
        java.time.LocalDate now = java.time.LocalDate.now();
        return "videos/" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy"))
                + "/" + now.format(java.time.format.DateTimeFormatter.ofPattern("MM")) + "/"
                + now.format(java.time.format.DateTimeFormatter.ofPattern("dd")) + "/"
                + System.currentTimeMillis()
                + org.apache.commons.lang3.RandomUtils.nextInt(100, 9999) + "."
                + StringUtils.substringAfterLast(sourceFileName, ".");
    }
}
