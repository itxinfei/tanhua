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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                // Mongo in 查询不保证返回顺序，按推荐 Redis 列表给出的顺序重排，避免推荐排序被打乱
                Map<Long, Video> videoById = new java.util.HashMap<>();
                for (Video v : videoList) {
                    videoById.put(v.getVid(), v);
                }
                List<Video> ordered = new ArrayList<>();
                for (Long vid : vidList) {
                    Video v = videoById.get(vid);
                    if (v != null) {
                        ordered.add(v);
                    }
                }
                pageInfo = new PageInfo<>();
                pageInfo.setRecords(ordered);
                pageInfo.setTotal(vids.length);
                pageInfo.setPages((vids.length + pageSize - 1) / pageSize);
            }
        }

        if(null == pageInfo){
            pageInfo = this.videoApi.queryVideoList(page, pageSize);
        }

        int total = pageInfo.getTotal() != null ? pageInfo.getTotal() : 0;
        pageResult.setCounts(total);
        pageResult.setPages((total + pageSize - 1) / pageSize);
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

        // 关注态以真实关注关系为准（其他页面关注/Redis 键过期也能正确显示），Redis 仅作降级兜底
        Set<Long> followedIds = null;
        try {
            List<Long> followIdList = this.videoApi.queryFollowUserIds(userId);
            followedIds = followIdList == null ? new HashSet<>() : new HashSet<>(followIdList);
        } catch (Exception e) {
            LOGGER.warn("查询用户关注列表失败，关注态降级为 Redis 键判断", e);
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

            // 是否关注：优先真实关注关系，查询失败时降级 Redis 已记录的关注键
            if (followedIds != null) {
                videoVo.setHasFocus(followedIds.contains(record.getUserId()) ? 1 : 0);
            } else {
                String focusKey = "VIDEO_FOLLOW_USER_" + userId + "_" + record.getUserId();
                videoVo.setHasFocus(this.redisTemplate.hasKey(focusKey) ? 1 : 0);
            }

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
        LOGGER.info("批量查询用户信息: userIds={}, 返回数量={}", userIds, userInfos == null ? 0 : userInfos.size());
        
        // 构建 userId -> UserInfo 的映射，避免双重循环匹配
        Map<Long, UserInfo> userInfoMap = new java.util.HashMap<>();
        if (userInfos != null) {
            for (UserInfo userInfo : userInfos) {
                userInfoMap.put(userInfo.getUserId(), userInfo);
            }
        }
        
        for (VideoVo videoVo : videoVoList) {
            UserInfo userInfo = userInfoMap.get(videoVo.getUserId());
            if (userInfo != null) {
                videoVo.setNickname(userInfo.getNickName());
                videoVo.setAvatar(userInfo.getLogo());
                // 签名：优先使用用户个性签名，其次用户标签，最后默认文案
                String signature = userInfo.getSignature();
                if (StringUtils.isEmpty(signature)) {
                    signature = userInfo.getTags();
                }
                videoVo.setSignature(StringUtils.isNotEmpty(signature) ? signature : "这个人很懒，什么都没写");
            } else {
                LOGGER.warn("视频 {} 的作者 {} 未找到用户信息", videoVo.getId(), videoVo.getUserId());
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

        List<Long> followIds = this.videoApi.queryFollowUserIds(user.getId());
        if (followIds == null || followIds.isEmpty()) {
            pageResult.setCounts(0);
            pageResult.setPages(0);
            pageResult.setItems(new ArrayList<>());
            return pageResult;
        }

        PageInfo<Video> pageInfo = this.videoApi.queryVideoListByUserIds(followIds, page, pageSize);
        int total = pageInfo.getTotal() != null ? pageInfo.getTotal() : 0;
        pageResult.setCounts(total);
        pageResult.setPages((total + pageSize - 1) / pageSize);
        List<Video> records = pageInfo.getRecords();
        if (records == null) {
            records = new ArrayList<>();
        }
        List<VideoVo> videoVoList = this.assembleVideoVoList(records, user.getId());
        pageResult.setItems(videoVoList);

        return pageResult;
    }

    /**
     * 查询「热点」流：按点赞数排序的热门视频（分页）
     *
     * 实现：取最新 100 条视频，从 Redis 读取点赞数排序后截取当前页。
     * 数据量增大后可改为 MongoDB 存 likeCount 字段 + 索引排序。
     */
    public PageResult queryHotVideoList(Integer page, Integer pageSize) {
        User user = UserThreadLocal.get();

        PageResult pageResult = new PageResult();
        pageResult.setPage(page);
        pageResult.setPagesize(pageSize);

        // 取最新 100 条视频作为候选池
        PageInfo<Video> allPage = this.videoApi.queryVideoList(1, 100);
        List<Video> records = allPage.getRecords();
        if (records == null || records.isEmpty()) {
            pageResult.setCounts(0);
            pageResult.setPages(0);
            pageResult.setItems(new ArrayList<>());
            return pageResult;
        }
        pageResult.setCounts(records.size());
        pageResult.setPages((records.size() + pageSize - 1) / pageSize);

        // 一次 multiGet 批量读出各视频的点赞数（Redis key: QUANZI_COMMENT_LIKE_{videoId}），再内存排序，
        // 避免比较器/循环内逐条 GET 产生 100 次串行网络往返
        List<String> likeKeys = new ArrayList<>();
        for (Video record : records) {
            likeKeys.add("QUANZI_COMMENT_LIKE_" + record.getId().toHexString());
        }
        List<String> likeValues = this.redisTemplate.opsForValue().multiGet(likeKeys);
        Map<String, Long> likeCountMap = new java.util.HashMap<>();
        for (int i = 0; i < likeKeys.size(); i++) {
            String val = likeValues != null && i < likeValues.size() ? likeValues.get(i) : null;
            long count = 0L;
            if (val != null) {
                try {
                    count = Long.parseLong(val);
                } catch (NumberFormatException e) {
                    // 脏数据（非数字）按 0 处理，避免整页排序失败
                    count = 0L;
                }
            }
            likeCountMap.put(likeKeys.get(i), count);
        }
        records.sort((a, b) -> Long.compare(
                likeCountMap.getOrDefault("QUANZI_COMMENT_LIKE_" + b.getId().toHexString(), 0L),
                likeCountMap.getOrDefault("QUANZI_COMMENT_LIKE_" + a.getId().toHexString(), 0L)));

        // 分页截取
        int startIndex = (page - 1) * pageSize;
        if (startIndex >= records.size()) {
            pageResult.setItems(new ArrayList<>());
            return pageResult;
        }
        int endIndex = Math.min(startIndex + pageSize, records.size());
        List<Video> pageRecords = records.subList(startIndex, endIndex);

        List<VideoVo> videoVoList = this.assembleVideoVoList(pageRecords, user.getId());
        pageResult.setItems(videoVoList);

        return pageResult;
    }

    /**
     * 查询「朋友」流：当前用户关注的作者发布的视频（分页）
     *
     * 与「关注」流逻辑一致，保留独立接口便于后续扩展
     * （如加入互相关注的好友、互动好友等更丰富的社交关系）。
     */
    public PageResult queryFriendVideoList(Integer page, Integer pageSize) {
        return this.queryFollowVideoList(page, pageSize);
    }

    /**
     * 查询「定位」流：有地理位置信息的视频（分页）
     */
    public PageResult queryLocationVideoList(Integer page, Integer pageSize) {
        User user = UserThreadLocal.get();

        PageResult pageResult = new PageResult();
        pageResult.setPage(page);
        pageResult.setPagesize(pageSize);

        PageInfo<Video> pageInfo = this.videoApi.queryLocationVideoList(page, pageSize);
        int total = pageInfo.getTotal() != null ? pageInfo.getTotal() : 0;
        pageResult.setCounts(total);
        pageResult.setPages((total + pageSize - 1) / pageSize);
        List<Video> records = pageInfo.getRecords();
        if (records == null || records.isEmpty()) {
            pageResult.setItems(new ArrayList<>());
            return pageResult;
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
