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
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.TimeUnit;

@Service
public class VideoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoService.class);

    /**
     * 推荐流打散候选池上限：一次取出足够多的视频做"按作者轮转打散"后再分页，
     * 避免最新视频恰好集中在单一作者时，首屏连续多条显示同一昵称
     * （用户会误以为"所有视频都是自己发的"）。数据量增大后可改为按页拉取。
     */
    private static final int CANDIDATE_LIMIT = 200;

    /**
     * 推荐候选池上限：由视频服务聚合排出「热度分降序 + (_id 降序)」的有序 vid 列表，
     * 服务端缓存该列表后按页切分，保证同一缓存窗口内翻页不重复、不跳项。
     */
    private static final int RECOMMEND_POOL_LIMIT = 300;

    /** 推荐有序 vid 列表缓存 TTL（秒）：只缓存排序结果，不缓存视频内容 */
    private static final long RECOMMEND_CACHE_SECONDS = 600L;

    private static final String RECOMMEND_KEY_PREFIX = "QUANZI_VIDEO_RECOMMEND_";

    /** 「附近」半径（公里）与最多返回条数，均可通过配置/环境变量覆盖 */
    @Value("${xfjy.video.nearby.radius-km:5}")
    private double nearbyRadiusKm;

    @Value("${xfjy.video.nearby.max-results:50}")
    private int nearbyMaxResults;

    /**
     * 按作者轮转打散：保持每个作者内部原有相对顺序（时间序/推荐序/热度序），
     * 将不同作者的视频交错排列，保证作者数 >= 2 时相邻视频不会来自同一作者。
     *
     * @param videos 原始有序列表（可为空）
     * @return 打散后的新列表（入参不会被修改）
     */
    private static List<Video> interleaveByAuthor(List<Video> videos) {
        if (videos == null || videos.size() <= 2) {
            return videos == null ? new ArrayList<>() : videos;
        }
        java.util.Map<Long, List<Video>> byAuthor = new java.util.LinkedHashMap<>();
        for (Video v : videos) {
            byAuthor.computeIfAbsent(v.getUserId(), k -> new ArrayList<>()).add(v);
        }
        if (byAuthor.size() <= 1) {
            return videos;
        }
        List<List<Video>> queues = new ArrayList<>(byAuthor.values());
        // 视频数多的作者排在前面，轮转时能被均匀铺开
        queues.sort((a, b) -> Integer.compare(b.size(), a.size()));
        int maxLen = 0;
        for (List<Video> q : queues) {
            maxLen = Math.max(maxLen, q.size());
        }
        List<Video> result = new ArrayList<>(videos.size());
        for (int i = 0; i < maxLen; i++) {
            for (List<Video> q : queues) {
                if (i < q.size()) {
                    result.add(q.get(i));
                }
            }
        }
        return result;
    }

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


    /**
     * 发布小视频
     *
     * @param longitude    经度字符串（可为空：H5 端 uni.getLocation 不支持逆地理，拿不到属正常路径）
     * @param latitude     纬度字符串（可为空）
     * @param locationName 城市/区域名（可为空，前端空时用距离标签展示）
     */
    public String saveVideo(MultipartFile picFile, MultipartFile videoFile,
                            String longitude, String latitude, String locationName) {

        User user = UserThreadLocal.get();

        Video video = new Video();
        video.setUserId(user.getId());
        video.setSeeType(1);
        // 位置落库：经纬度换成 geo 由视频服务统一构造（[lng,lat]），locationName 允许为空
        video.setLongitude(StringUtils.trimToNull(longitude));
        video.setLatitude(StringUtils.trimToNull(latitude));
        video.setLocationName(StringUtils.trimToNull(locationName));

        try {
            // MinIO 未配置（凭证环境变量未注入）时快速失败
            if (this.minioClient == null) {
                LOGGER.error("MinIO 未配置，无法上传视频，请检查 MINIO_ACCESS_KEY/MINIO_SECRET_KEY 环境变量");
                return null;
            }

            // 上传图片
            PicUploadResult picUploadResult = this.picUploadService.upload(picFile);
            video.setPicUrl(picUploadResult.getName());

            //上传视频到 MinIO（流式上传，避免大视频 getBytes() 全量读入内存导致 OOM）
            String videoFilePath = getVideoFilePath(videoFile.getOriginalFilename());
            try (java.io.InputStream in = videoFile.getInputStream()) {
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

        // 「推荐」= 热度分排序（不是时间流）：点赞×3 + 评论×5 + 播放×1，再乘时间衰减 1/(1+天数)
        // 排序与分页稳定性：视频服务一次排出最多 RECOMMEND_POOL_LIMIT 条有序 vid，
        // 这里把「已排好序的 vid 列表」缓存进 Redis，后续翻页只做切片，
        // 因而同一缓存窗口内热度实时变化不会造成重复/漏项；缓存丢失时自愈重建。
        List<Long> rankedVids = loadRankedVidList(user.getId());
        if (rankedVids.isEmpty()) {
            pageResult.setCounts(0);
            pageResult.setPages(0);
            pageResult.setItems(new ArrayList<>());
            return pageResult;
        }

        int total = rankedVids.size();
        int startIndex = (page - 1) * pageSize;
        if (pageSize <= 0 || startIndex >= total) {
            pageResult.setCounts(total);
            pageResult.setPages(pageSize > 0 ? (total + pageSize - 1) / pageSize : 0);
            pageResult.setItems(new ArrayList<>());
            return pageResult;
        }
        int endIndex = Math.min(startIndex + pageSize, total);
        List<Long> pageVids = new ArrayList<>(rankedVids.subList(startIndex, endIndex));

        List<Video> pageRecords = queryByVidsInOrder(pageVids);
        // 自愈：缓存里的视频被删除后首屏可能为空，强制重建一次排序缓存
        if (pageRecords.isEmpty() && page == 1) {
            this.redisTemplate.delete(RECOMMEND_KEY_PREFIX + user.getId());
            rankedVids = loadRankedVidList(user.getId());
            total = rankedVids.size();
            endIndex = Math.min(pageSize, total);
            if (endIndex > 0) {
                pageRecords = queryByVidsInOrder(new ArrayList<>(rankedVids.subList(0, endIndex)));
            }
        }

        pageResult.setCounts(total);
        pageResult.setPages(pageSize > 0 ? (total + pageSize - 1) / pageSize : 0);
        pageResult.setItems(assembleVideoVoList(pageRecords, user.getId()));
        return pageResult;
    }

    /** 读取已排好序的推荐 vid 列表；缓存未命中时调用视频服务聚合生成并写回（自愈重建） */
    private List<Long> loadRankedVidList(Long userId) {
        List<Long> cached = parseVidList(this.redisTemplate.opsForValue().get(RECOMMEND_KEY_PREFIX + userId));
        if (!cached.isEmpty()) {
            return cached;
        }
        List<Video> pool = this.videoApi.queryRecommendVideoList(RECOMMEND_POOL_LIMIT);
        List<Long> vids = new ArrayList<>();
        if (pool != null) {
            for (Video video : pool) {
                if (video.getVid() != null) {
                    vids.add(video.getVid());
                }
            }
        }
        if (!vids.isEmpty()) {
            this.redisTemplate.opsForValue().set(RECOMMEND_KEY_PREFIX + userId,
                    StringUtils.join(vids, ','), RECOMMEND_CACHE_SECONDS, TimeUnit.SECONDS);
        }
        return vids;
    }

    /** 解析 Redis 中逗号分隔的 vid 列表，脏数据（非数字）跳过而不是抛错 */
    private List<Long> parseVidList(String value) {
        List<Long> vids = new ArrayList<>();
        if (StringUtils.isEmpty(value)) {
            return vids;
        }
        for (String vid : StringUtils.split(value, ',')) {
            try {
                vids.add(Long.valueOf(vid));
            } catch (NumberFormatException e) {
                LOGGER.warn("推荐 vid 列表含非数字项，已跳过: value={}", vid);
            }
        }
        return vids;
    }

    /** 按给定 vid 顺序回表查询（Mongo in 查询不保证顺序，必须按推荐顺序重排） */
    private List<Video> queryByVidsInOrder(List<Long> vids) {
        List<Video> result = new ArrayList<>();
        if (vids.isEmpty()) {
            return result;
        }
        List<Video> videoList = this.videoApi.queryVideoListByPids(vids);
        Map<Long, Video> videoById = new java.util.HashMap<>();
        if (videoList != null) {
            for (Video v : videoList) {
                videoById.put(v.getVid(), v);
            }
        }
        for (Long vid : vids) {
            Video v = videoById.get(vid);
            if (v != null) {
                result.add(v);
            }
        }
        return result;
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
            // 位置：城市名可为空（H5 端不支持逆地理），前端空时用 distance 展示距离标签，
            // 绝不把 "lng,lat" 坐标串直接展示给用户
            videoVo.setLocationName(record.getLocationName());
            videoVo.setDistance(record.getDistance());
            // 签名：取用户标签作为个性签名（UserInfo 无独立签名字段，tags 为最接近的真实数据）
            videoVo.setSignature(null);

            // 评论数：优先落库计数（Video.commentCount），历史数据缺失时才回退聚合统计
            Integer dbCommentCount = record.getCommentCount();
            if (dbCommentCount != null) {
                videoVo.setCommentCount(dbCommentCount);
            } else {
                Long commentCount = commentCountMap.get(videoVo.getId());
                videoVo.setCommentCount(commentCount == null ? 0 : commentCount.intValue());
            }

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
            // 点赞数：优先落库计数（Video.likeCount），历史数据缺失时回退 Redis 赞数键
            if (record.getLikeCount() != null) {
                videoVo.setLikeCount(record.getLikeCount());
            } else {
                String value = this.redisTemplate.opsForValue().get(likeCommentKey);
                int likeCount = 0;
                if (StringUtils.isNotEmpty(value)) {
                    try {
                        likeCount = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        LOGGER.warn("视频点赞数缓存非数字，兜底为0: key={}, value={}", likeCommentKey, value);
                    }
                }
                videoVo.setLikeCount(likeCount);
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

        // 按作者轮转打散：仍以热度为准，但同一作者的高赞视频不连续霸屏
        List<Video> ranked = interleaveByAuthor(records);

        // 分页截取
        int startIndex = (page - 1) * pageSize;
        if (startIndex >= ranked.size()) {
            pageResult.setItems(new ArrayList<>());
            return pageResult;
        }
        int endIndex = Math.min(startIndex + pageSize, ranked.size());
        List<Video> pageRecords = new ArrayList<>(ranked.subList(startIndex, endIndex));

        List<VideoVo> hotVoList = this.assembleVideoVoList(pageRecords, user.getId());
        pageResult.setItems(hotVoList);

        return pageResult;
    }

    /**
     * 查询「附近」流：有坐标时按 geo 距离升序返回；无坐标（用户未授权/旧数据没 geo）时
     * 绝不静默返回空列表，而是回退全站热门并带 fallback 标记，前端据此提示而非显示坏页。
     *
     * @param lat 纬度，可为空（空即视为没有定位权限）
     * @param lng 经度，可为空
     */
    public PageResult queryLocationVideoList(Integer page, Integer pageSize, Double lat, Double lng) {
        User user = UserThreadLocal.get();

        PageResult pageResult = new PageResult();
        pageResult.setPage(page);
        pageResult.setPagesize(pageSize);
        pageResult.setFallback(false);

        if (lat == null || lng == null) {
            return nearbyFallback(pageResult, user.getId(), page, pageSize);
        }

        int cappedSize = Math.min(pageSize, Math.max(1, nearbyMaxResults));
        int cappedPage = page;
        // 总条数上限：附近最多返回 nearbyMaxResults 条，超出即视为没有更多
        if ((cappedPage - 1) * cappedSize >= nearbyMaxResults) {
            pageResult.setCounts(0);
            pageResult.setPages(0);
            pageResult.setItems(new ArrayList<>());
            return pageResult;
        }

        PageInfo<Video> geoPage = this.videoApi.queryLocationVideoList(
                cappedPage, cappedSize, lat, lng, nearbyRadiusKm);
        List<Video> records = geoPage == null ? null : geoPage.getRecords();
        if (records == null || (records.isEmpty() && cappedPage == 1)) {
            // 半径内没有带坐标的视频（旧数据必然如此）：回退全站热门，不返回空列表
            return nearbyFallback(pageResult, user.getId(), page, pageSize);
        }

        int total = geoPage.getTotal() != null ? Math.min(geoPage.getTotal(), nearbyMaxResults) : records.size();
        pageResult.setCounts(total);
        pageResult.setPages((total + cappedSize - 1) / cappedSize);
        pageResult.setItems(assembleVideoVoList(records, user.getId()));
        return pageResult;
    }

    /** 「附近」回退全站热门：复用推荐排序缓存里的有序 vid 列表，并标记 fallback=true */
    private PageResult nearbyFallback(PageResult pageResult, Long userId, Integer page, Integer pageSize) {
        List<Long> rankedVids = loadRankedVidList(userId);
        if (rankedVids.isEmpty()) {
            pageResult.setCounts(0);
            pageResult.setPages(0);
            pageResult.setItems(new ArrayList<>());
            pageResult.setFallback(true);
            return pageResult;
        }
        int total = Math.min(rankedVids.size(), nearbyMaxResults);
        int startIndex = (page - 1) * pageSize;
        pageResult.setFallback(true);
        if (pageSize <= 0 || startIndex >= total) {
            pageResult.setCounts(total);
            pageResult.setPages(pageSize > 0 ? (total + pageSize - 1) / pageSize : 0);
            pageResult.setItems(new ArrayList<>());
            return pageResult;
        }
        int endIndex = Math.min(startIndex + pageSize, total);
        List<Video> records = queryByVidsInOrder(new ArrayList<>(rankedVids.subList(startIndex, endIndex)));
        pageResult.setCounts(total);
        pageResult.setPages((total + pageSize - 1) / pageSize);
        pageResult.setItems(assembleVideoVoList(records, userId));
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

    /** 当前用户是否已点赞该视频（点赞去重依据，避免同一用户重复点赞导致落库计数虚高） */
    public boolean hasLikedVideo(String videoId) {
        User user = UserThreadLocal.get();
        return this.redisTemplate.hasKey("QUANZI_COMMENT_LIKE_USER_" + user.getId() + "_" + videoId);
    }

    /** 点赞/取消赞同步落库计数（+1 / -1）；负数边界由视频服务以 likeCount > 0 为条件保证 */
    public void incVideoLikeCount(String videoId, int delta) {
        this.videoApi.incVideoCounter(videoId, "likeCount", delta);
    }

    /** 评论写入/删除同步落库计数（+1 / -1），删除时同样不允许出现负数 */
    public void incVideoCommentCount(String videoId, int delta) {
        this.videoApi.incVideoCounter(videoId, "commentCount", delta);
    }

    /**
     * 播放上报（防刷硬要求）：Redis 去重键 VIDEO_PLAY_DEDUP_{userId}_{videoId}，
     * 同一用户对同一视频在窗口期内只计一次；键名明确是「用户×视频」维度。
     */
    public void reportVideoPlay(String videoId) {
        User user = UserThreadLocal.get();
        String dedupKey = "VIDEO_PLAY_DEDUP_" + user.getId() + "_" + videoId;
        Boolean first = this.redisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", 6, TimeUnit.HOURS);
        if (Boolean.TRUE.equals(first)) {
            this.videoApi.incVideoCounter(videoId, "playCount", 1);
        }
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
