package com.xfjy.service.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.mongodb.client.result.DeleteResult;
import com.xfjy.service.pojo.FollowUser;
import com.xfjy.service.pojo.Video;
import com.xfjy.service.service.IdService;
import com.xfjy.service.vo.PageInfo;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.NearQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.bson.Document;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/video")
public class VideoApiImpl implements VideoApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoApiImpl.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private IdService idService;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 发送视频评分消息（消息格式与 xfjy-recommend 消费端兼容）
     *
     * @param type  1-发布，2-点赞，3-取消点赞，4-评论
     */
    private void sendVideoMsg(Long userId, String videoId, Long vid, Integer type) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("userId", userId);
            msg.put("date", System.currentTimeMillis());
            msg.put("videoId", videoId);
            msg.put("vid", vid);
            msg.put("type", type);
            this.rocketMQTemplate.convertAndSend("xfjy-video", (Object) msg);
        } catch (Exception e) {
            LOGGER.error("视频评分消息发送失败! type = " + type + ", videoId = " + videoId, e);
        }
    }

    @Override
    @PostMapping("/saveVideo")
    public String saveVideo(@RequestBody Video video) {

        if (video.getUserId() == null) {
            return null;
        }

        video.setId(ObjectId.get());
        video.setCreated(System.currentTimeMillis());
        // 计数字段初始化：旧数据/旧调用方缺失时补 0，避免热度聚合与前端展示读到 null
        if (video.getLikeCount() == null) {
            video.setLikeCount(0);
        }
        if (video.getCommentCount() == null) {
            video.setCommentCount(0);
        }
        if (video.getPlayCount() == null) {
            video.setPlayCount(0);
        }
        // 经纬度字符串 -> GeoJsonPoint([lng, lat])，供「附近」2dsphere 距离查询使用。
        // 解析失败/越界不阻塞发布，只是该视频没有 geo（查询侧会回退全站热门）。
        if (video.getGeo() == null && video.getLongitude() != null && video.getLatitude() != null) {
            try {
                double lng = Double.parseDouble(video.getLongitude());
                double lat = Double.parseDouble(video.getLatitude());
                if (lng >= -180D && lng <= 180D && lat >= -90D && lat <= 90D) {
                    video.setGeo(new GeoJsonPoint(lng, lat));
                }
            } catch (NumberFormatException e) {
                LOGGER.warn("视频经纬度非法，跳过 geo 写入: lng={}, lat={}",
                        video.getLongitude(), video.getLatitude());
            }
        }
        //自增长id
        video.setVid(this.idService.createId("video", video.getId().toHexString()));
        this.mongoTemplate.save(video);

        // 评分事件：发布视频（type=1）
        this.sendVideoMsg(video.getUserId(), video.getId().toHexString(), video.getVid(), 1);

        return video.getId().toHexString();
    }

    @Override
    @GetMapping("/queryVideoList")
    public PageInfo<Video> queryVideoList(@RequestParam("page") Integer page,
                                          @RequestParam("pageSize") Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        Pageable pageable = PageRequest.of(p - 1, size, Sort.by(Sort.Order.desc("created")));
        Query query = new Query().with(pageable);
        List<Video> videos = this.mongoTemplate.find(query, Video.class);
        PageInfo<Video> pageInfo = new PageInfo<>();
        pageInfo.setRecords(videos);
        pageInfo.setPageNum(p);
        pageInfo.setPageSize(size);
        long total = this.mongoTemplate.count(new Query(), Video.class);
        pageInfo.setTotal((int) total);
        pageInfo.setPages(size > 0 ? (int) ((total + size - 1) / size) : 0);
        return pageInfo;
    }

    @Override
    @PostMapping("/followUser")
    public Boolean followUser(@RequestParam("userId") Long userId,
                              @RequestParam("followUserId") Long followUserId) {
        try {
            // 先查后插：已关注则幂等返回 true，防止重复关注记录
            Query existQuery = Query.query(Criteria.where("userId").is(userId).and("followUserId").is(followUserId));
            if (this.mongoTemplate.exists(existQuery, FollowUser.class)) {
                return true;
            }
            FollowUser followUser = new FollowUser();
            followUser.setId(ObjectId.get());
            followUser.setUserId(userId);
            followUser.setFollowUserId(followUserId);
            followUser.setCreated(System.currentTimeMillis());
            this.mongoTemplate.save(followUser);
            return true;
        } catch (Exception e) {
            LOGGER.error("视频服务处理异常", e);
        }
        return false;
    }

    @Override
    @PostMapping("/disFollowUser")
    public Boolean disFollowUser(@RequestParam("userId") Long userId,
                                 @RequestParam("followUserId") Long followUserId) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("followUserId").is(followUserId));
        DeleteResult deleteResult = this.mongoTemplate.remove(query, FollowUser.class);
        return deleteResult.getDeletedCount() > 0;
    }

    @Override
    @PostMapping("/queryVideoListByPids")
    public List<Video> queryVideoListByPids(@RequestBody List<Long> vids) {
        Query query = Query.query(Criteria.where("vid").in(vids));
        return this.mongoTemplate.find(query, Video.class);
    }

    @Override
    @PostMapping("/recordVideoMsg")
    public Boolean recordVideoMsg(@RequestParam("userId") Long userId,
                                  @RequestParam("videoId") String videoId,
                                  @RequestParam("type") Integer type) {
        Video video = this.mongoTemplate.findById(new ObjectId(videoId), Video.class);
        if (video == null) {
            return false;
        }
        this.sendVideoMsg(userId, videoId, video.getVid(), type);
        return true;
    }

    @Override
    @PostMapping("/removeVideo")
    public boolean removeVideo(@RequestParam("videoId") String videoId) {
        if (videoId == null || videoId.isEmpty()) {
            return false;
        }
        try {
            Query query = Query.query(Criteria.where("_id").is(new ObjectId(videoId)));
            DeleteResult result = this.mongoTemplate.remove(query, Video.class);
            if (result.getDeletedCount() > 0) {
                LOGGER.info("视频已下架 videoId={}", videoId);
                return true;
            }
            return false;
        } catch (IllegalArgumentException e) {
            LOGGER.warn("removeVideo 非法的 videoId: {}", videoId);
            return false;
        }
    }

    @Override
    @GetMapping("/queryAllVideoList")
    public PageInfo<Video> queryAllVideoList(@RequestParam(value = "page", required = false) Integer page,
                                              @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                              @RequestParam(value = "keyword", required = false) String keyword,
                                              @RequestParam(value = "userId", required = false) Long userId,
                                              @RequestParam(value = "startTime", required = false) Long startTime,
                                              @RequestParam(value = "endTime", required = false) Long endTime) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1 || pageSize > 100) ? 10 : pageSize;

        Criteria criteria = new Criteria();
        java.util.List<Criteria> conditions = new java.util.ArrayList<>();
        if (userId != null) {
            conditions.add(Criteria.where("userId").is(userId));
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            conditions.add(Criteria.where("text").regex(".*" + java.util.regex.Pattern.quote(keyword.trim()) + ".*", "i"));
        }
        if (startTime != null) {
            conditions.add(Criteria.where("created").gte(startTime));
        }
        if (endTime != null) {
            conditions.add(Criteria.where("created").lte(endTime));
        }
        if (!conditions.isEmpty()) {
            criteria.andOperator(conditions.toArray(new Criteria[0]));
        }

        long total = this.mongoTemplate.count(new Query(criteria), Video.class);

        Pageable pageable = PageRequest.of(p - 1, size, Sort.by(Sort.Order.desc("created")));
        List<Video> records = this.mongoTemplate.find(new Query(criteria).with(pageable), Video.class);

        PageInfo<Video> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(p);
        pageInfo.setPageSize(size);
        pageInfo.setTotal((int) total);
        pageInfo.setPages(size > 0 ? (int) ((total + size - 1) / size) : 0);
        pageInfo.setRecords(records);
        return pageInfo;
    }

    @Override
    @GetMapping("/queryFollowUserIds")
    public List<Long> queryFollowUserIds(@RequestParam("userId") Long userId) {
        Query query = Query.query(Criteria.where("userId").is(userId));
        List<FollowUser> list = this.mongoTemplate.find(query, FollowUser.class);
        List<Long> ids = new java.util.ArrayList<>();
        for (FollowUser fu : list) {
            ids.add(fu.getFollowUserId());
        }
        return ids;
    }

    @Override
    @GetMapping("/queryVideoListByUserIds")
    public PageInfo<Video> queryVideoListByUserIds(@RequestParam("userIds") List<Long> userIds,
                                                  @RequestParam("page") Integer page,
                                                  @RequestParam("pageSize") Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        if (userIds == null || userIds.isEmpty()) {
            PageInfo<Video> empty = new PageInfo<>();
            empty.setRecords(new java.util.ArrayList<>());
            empty.setPageNum(p);
            empty.setPageSize(size);
            empty.setTotal(0);
            empty.setPages(0);
            return empty;
        }
        Pageable pageable = PageRequest.of(p - 1, size, Sort.by(Sort.Order.desc("created")));
        Query query = Query.query(Criteria.where("userId").in(userIds)).with(pageable);
        List<Video> videos = this.mongoTemplate.find(query, Video.class);
        PageInfo<Video> pageInfo = new PageInfo<>();
        pageInfo.setRecords(videos);
        pageInfo.setPageNum(p);
        pageInfo.setPageSize(size);
        long total = this.mongoTemplate.count(Query.query(Criteria.where("userId").in(userIds)), Video.class);
        pageInfo.setTotal((int) total);
        pageInfo.setPages(size > 0 ? (int) ((total + size - 1) / size) : 0);
        return pageInfo;
    }

    @Override
    @PostMapping("/batchRemoveVideo")
    public Map<String, Object> batchRemoveVideo(@RequestBody List<String> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("success", 0);
            empty.put("failed", 0);
            return empty;
        }

        int success = 0;
        int failed = 0;
        for (String videoId : videoIds) {
            try {
                if (removeVideo(videoId)) {
                    success++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                LOGGER.error("批量删除视频失败 videoId={}", videoId, e);
                failed++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        LOGGER.info("批量删除视频完成: success={}, failed={}", success, failed);
        return result;
    }

    /**
     * 「附近」：$geoNear 必须作为管道首阶段，distanceField=distance 会把距离（米）写回结果文档，
     * 再 skip/limit 完成分页。geo 字段没有 2dsphere 索引时该查询会报错，索引由启动期初始化创建。
     */
    @Override
    @GetMapping("/queryLocationVideoList")
    public PageInfo<Video> queryLocationVideoList(@RequestParam("page") Integer page,
                                                   @RequestParam("pageSize") Integer pageSize,
                                                   @RequestParam("lat") Double lat,
                                                   @RequestParam("lng") Double lng,
                                                   @RequestParam("radiusKm") Double radiusKm) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        PageInfo<Video> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(p);
        pageInfo.setPageSize(size);
        if (lat == null || lng == null) {
            pageInfo.setRecords(new java.util.ArrayList<>());
            pageInfo.setTotal(0);
            pageInfo.setPages(0);
            return pageInfo;
        }
        double radius = (radiusKm == null || radiusKm <= 0) ? 5D : radiusKm;
        GeoJsonPoint point = new GeoJsonPoint(lng, lat);

        NearQuery near = NearQuery.near(point)
                .maxDistance(new Distance(radius, Metrics.KILOMETERS))
                .query(new Query());
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.geoNear(near, "distance"),
                Aggregation.skip((long) (p - 1) * size),
                Aggregation.limit(size));
        List<Video> records = this.mongoTemplate
                .aggregate(aggregation, "video", Video.class)
                .getMappedResults();

        long total = this.mongoTemplate.count(
                Query.query(Criteria.where("geo").nearSphere(point).maxDistance(radius * 1000D)), Video.class);

        pageInfo.setRecords(records);
        pageInfo.setTotal((int) total);
        pageInfo.setPages(size > 0 ? (int) ((total + size - 1) / size) : 0);
        return pageInfo;
    }

    /** $ifNull，计数字段缺失（历史数据）时按 0 参与热度计算 */
    private static Document ifNullZero(String field) {
        return new Document("$ifNull", Arrays.asList(field, 0));
    }

    /**
     * 推荐候选池：热度分降序，并以 (_id 降序) 兜底保证 (score, id) 稳定排序，翻页不重复不跳项。
     *
     * <pre>
     * 热度分 = (likeCount×3 + commentCount×5 + playCount×1) × 1/(1 + 发布距今毫秒/86400000)
     * 即：互动量决定基础分，发布时间每过 24 小时权重衰减一半（分母 1+天数）。
     * </pre>
     */
    @Override
    @GetMapping("/queryRecommendVideoList")
    public List<Video> queryRecommendVideoList(@RequestParam("limit") Integer limit) {
        int lim = (limit == null || limit < 1) ? 200 : Math.min(limit, 500);
        long now = System.currentTimeMillis();
        AggregationOperation addScore = context -> new Document("$addFields",
                new Document("heatScore", new Document("$multiply", Arrays.asList(
                        new Document("$add", Arrays.asList(
                                new Document("$multiply", Arrays.asList(ifNullZero("$likeCount"), 3)),
                                new Document("$multiply", Arrays.asList(ifNullZero("$commentCount"), 5)),
                                new Document("$multiply", Arrays.asList(ifNullZero("$playCount"), 1))
                        )),
                        new Document("$divide", Arrays.asList(1, new Document("$add", Arrays.asList(
                                1,
                                new Document("$divide", Arrays.asList(
                                        new Document("$subtract", Arrays.asList(now, ifNullZero("$created"))),
                                        86400000D)
                                )
                        )))
                )))));

        Aggregation aggregation = Aggregation.newAggregation(
                addScore,
                Aggregation.sort(Sort.by(Sort.Order.desc("heatScore"), Sort.Order.desc("_id"))),
                Aggregation.limit(lim));
        return this.mongoTemplate.aggregate(aggregation, "video", Video.class).getMappedResults();
    }

    /** 允许自增的计数字段白名单，防止外部任意写 Mongo 字段 */
    private static final Set<String> COUNTER_FIELDS = new HashSet<>(Arrays.asList(
            "likeCount", "commentCount", "playCount"));

    @Override
    @PostMapping("/incVideoCounter")
    public Boolean incVideoCounter(@RequestParam("videoId") String videoId,
                                   @RequestParam("field") String field,
                                   @RequestParam("delta") Integer delta) {
        if (videoId == null || delta == null || delta == 0 || !COUNTER_FIELDS.contains(field)) {
            return false;
        }
        try {
            ObjectId id = new ObjectId(videoId);
            Criteria criteria = Criteria.where("_id").is(id);
            if (delta < 0) {
                // 减计数时只更新当前值 > 0 的文档，保证不出现负数
                criteria = criteria.and(field).gt(0);
            }
            Update update = new Update().inc(field, delta);
            return this.mongoTemplate.updateFirst(Query.query(criteria), update, Video.class)
                    .getModifiedCount() > 0;
        } catch (IllegalArgumentException e) {
            LOGGER.warn("incVideoCounter 非法的 videoId: {}", videoId);
            return false;
        }
    }

    @Override
    @PostMapping("/setVideoCounters")
    public Boolean setVideoCounters(@RequestParam("videoId") String videoId,
                                    @RequestParam("likeCount") Integer likeCount,
                                    @RequestParam("commentCount") Integer commentCount) {
        if (videoId == null || likeCount == null || commentCount == null) {
            return false;
        }
        try {
            Update update = new Update()
                    .set("likeCount", Math.max(0, likeCount))
                    .set("commentCount", Math.max(0, commentCount));
            return this.mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(new ObjectId(videoId))), update, Video.class)
                    .getModifiedCount() > 0;
        } catch (IllegalArgumentException e) {
            LOGGER.warn("setVideoCounters 非法的 videoId: {}", videoId);
            return false;
        }
    }

    /**
     * 回填专用写入：查询条件带上「目标字段当前为 null」的 or 条件，
     * 因此已存在的非空值不会被覆盖；两个字段都已非空时不会命中任何文档。
     */
    @Override
    @PostMapping("/fillVideoCountersIfNull")
    public Boolean fillVideoCountersIfNull(@RequestParam("videoId") String videoId,
                                           @RequestParam(value = "likeCount", required = false) Integer likeCount,
                                           @RequestParam(value = "commentCount", required = false) Integer commentCount) {
        if (videoId == null || (likeCount == null && commentCount == null)) {
            return false;
        }
        try {
            Update update = new Update();
            java.util.List<Criteria> nullFields = new java.util.ArrayList<>();
            if (likeCount != null) {
                update.set("likeCount", Math.max(0, likeCount));
                nullFields.add(Criteria.where("likeCount").is(null));
            }
            if (commentCount != null) {
                update.set("commentCount", Math.max(0, commentCount));
                nullFields.add(Criteria.where("commentCount").is(null));
            }
            Criteria criteria = Criteria.where("_id").is(new ObjectId(videoId))
                    .andOperator(new Criteria().orOperator(nullFields.toArray(new Criteria[0])));
            return this.mongoTemplate.updateFirst(Query.query(criteria), update, Video.class)
                    .getModifiedCount() > 0;
        } catch (IllegalArgumentException e) {
            LOGGER.warn("fillVideoCountersIfNull 非法的 videoId: {}", videoId);
            return false;
        }
    }
}
