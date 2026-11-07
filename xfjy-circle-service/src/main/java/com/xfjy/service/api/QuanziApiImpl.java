package com.xfjy.service.api;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.mongodb.client.result.UpdateResult;
import com.xfjy.service.pojo.*;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.bson.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quanzi")
public class QuanziApiImpl implements QuanZiApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuanziApiImpl.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private IdService idService;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    /** 分页参数防御：page 为 null 或 <1 时按第 1 页处理，避免 PageRequest.of(-1) 抛异常 */
    private int safePage(Integer page) {
        return (page == null || page < 1) ? 1 : page;
    }

    /** 安全删除：失败仅记录日志不抛异常（用于补偿回滚，避免回滚失败掩盖原始异常） */
    private void safeRemove(String desc, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOGGER.error("补偿回滚删除失败: {}", desc, e);
        }
    }

    /**
     * 发送圈子评分消息（原 server 端 QuanziMQService 逻辑迁入，阶段三）
     * 消息格式与消费方 xfjy-recommend 的 QuanZiMsgConsumer 保持兼容：type/publishId/date/userId/pid
     *
     * @param type    1-发动态，2-浏览动态，3-点赞，4-喜欢，5-评论，6-取消点赞，7-取消喜欢
     * @param userId  操作人
     * @param publish 动态对象（提供 publishId 与 pid；视频互动时为 null 不发送，与原行为一致）
     */
    private void sendScoringMsg(Integer type, Long userId, Publish publish) {
        try {
            if (publish == null || publish.getPid() == null) {
                return;
            }
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", type);
            msg.put("publishId", publish.getId().toHexString());
            msg.put("date", System.currentTimeMillis());
            msg.put("userId", userId);
            msg.put("pid", publish.getPid());

            this.rocketMQTemplate.convertAndSend("xfjy-quanzi", (Object) msg);
        } catch (Exception e) {
            LOGGER.error("圈子评分消息发送失败! type={}, userId={}", type, userId, e);
        }
    }

    @Override
    @PostMapping("/savePublish")
    public String savePublish(@RequestBody Publish publish) {

        //校验publish对象
        if (publish.getUserId() == null) {
            return null;
        }

        //填充数据
        publish.setId(ObjectId.get());
        publish.setCreated(System.currentTimeMillis()); //发布时间
        publish.setSeeType(1); //查看权限

        //增加自增长的pid
        publish.setPid(this.idService.createId("publish", publish.getId().toHexString()));

        //第一步：保存动态信息
        this.mongoTemplate.save(publish);

        //第二步：写入到自己的相册表中（失败则补偿回滚动态）
        Album album = new Album();
        album.setId(ObjectId.get());
        album.setPublishId(publish.getId());//动态id
        album.setCreated(System.currentTimeMillis());

        try {
            this.mongoTemplate.save(album, "quanzi_album_" + publish.getUserId());
        } catch (Exception e) {
            LOGGER.error("相册写入失败，补偿回滚动态: publishId={}", publish.getId(), e);
            safeRemove("动态-" + publish.getId(), () -> this.mongoTemplate.remove(publish));
            return null;
        }

        //第三步：将动态写入好友的时间线表（失败则补偿回滚相册+动态+已写入的时间线）
        Query query = Query.query(Criteria.where("userId").is(publish.getUserId()));
        List<Users> users = this.mongoTemplate.find(query, Users.class);

        //记录已写入的时间线（集合名与记录id），用于补偿回滚
        List<ObjectId> savedTimeLineIds = new ArrayList<>();
        List<String> savedTimeLineCollections = new ArrayList<>();

        try {
            // 写入自己的时间线表（用户自己也能看到自己发布的动态）
            TimeLine selfTimeLine = new TimeLine();
            selfTimeLine.setId(ObjectId.get());
            selfTimeLine.setUserId(publish.getUserId());
            selfTimeLine.setPublishId(publish.getId());
            selfTimeLine.setDate(System.currentTimeMillis());
            String selfCollection = "quanzi_time_line_" + publish.getUserId();
            this.mongoTemplate.save(selfTimeLine, selfCollection);
            savedTimeLineIds.add(selfTimeLine.getId());
            savedTimeLineCollections.add(selfCollection);

            // 写入好友的时间线表
            for (Users user : users) {
                TimeLine timeLine = new TimeLine();
                timeLine.setId(ObjectId.get());
                timeLine.setUserId(publish.getUserId());
                timeLine.setPublishId(publish.getId());
                timeLine.setDate(System.currentTimeMillis());

                String collection = "quanzi_time_line_" + user.getFriendId();
                this.mongoTemplate.save(timeLine, collection);
                savedTimeLineIds.add(timeLine.getId());
                savedTimeLineCollections.add(collection);
            }
        } catch (Exception e) {
            LOGGER.error("时间线写入失败，补偿回滚相册+动态: publishId={}", publish.getId(), e);
            //回滚已写入的时间线（每步独立防护，避免回滚失败掩盖原始异常）
            for (int i = 0; i < savedTimeLineIds.size(); i++) {
                final int idx = i;
                safeRemove("时间线-" + savedTimeLineCollections.get(idx), () -> this.mongoTemplate.remove(
                        Query.query(Criteria.where("id").is(savedTimeLineIds.get(idx))),
                        TimeLine.class, savedTimeLineCollections.get(idx)));
            }
            //回滚相册
            safeRemove("相册-" + publish.getUserId(), () -> this.mongoTemplate.remove(
                    Query.query(Criteria.where("id").is(album.getId())),
                    Album.class, "quanzi_album_" + publish.getUserId()));
            //回滚动态
            safeRemove("动态-" + publish.getId(), () -> this.mongoTemplate.remove(publish));
            return null;
        }

        // 评分事件：发动态（type=1）——消息发送为尽力而为，不参与回滚
        this.sendScoringMsg(1, publish.getUserId(), publish);

        return publish.getId().toHexString();
    }

    @Override
    @GetMapping("/queryPublishList")
    public PageInfo<Publish> queryPublishList(@RequestParam(value = "userId", required = false) Long userId,
                                              @RequestParam(value = "page", required = false) Integer page,
                                              @RequestParam(value = "pageSize", required = false) Integer pageSize) {

        int p = safePage(page);
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        Pageable pageable = PageRequest.of(p - 1, size, Sort.by(Sort.Order.desc("date")));
        // 软删除过滤：时间线仅查未删除记录
        Query query = new Query(Criteria.where("deleted").ne(true)).with(pageable);

        String tableName = "quanzi_time_line_";
        if (null == userId) {
            //查询推荐动态
            tableName += "recommend";
        } else {
            //查询好友动态
            tableName += userId;
        }

        // 查询自己的时间线数据
        List<TimeLine> timeLines = this.mongoTemplate.find(query, TimeLine.class, tableName);

        List<ObjectId> ids = new ArrayList<>();
        for (TimeLine timeLine : timeLines) {
            ids.add(timeLine.getPublishId());
        }

        // 软删除过滤：动态仅查未删除记录
        Query queryPublish = Query.query(Criteria.where("id").in(ids).and("deleted").ne(true)).with(Sort.by(Sort.Order.desc("created")));
        //查询动态信息
        List<Publish> publishList = this.mongoTemplate.find(queryPublish, Publish.class);

        // 封装分页对象
        PageInfo<Publish> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(p);
        pageInfo.setPageSize(size);
        // 计算总数：用时间线总数（时间线是分页的依据，records 是关联查出的 publish）
        // 软删除过滤：总数仅统计未删除的时间线
        long total = this.mongoTemplate.count(new Query(Criteria.where("deleted").ne(true)), TimeLine.class, tableName);
        pageInfo.setTotal((int) total);
        pageInfo.setPages(size > 0 ? (int) ((total + size - 1) / size) : 0);
        pageInfo.setRecords(publishList);

        return pageInfo;
    }

    @Override
    @PostMapping("/saveLikeComment")
    public boolean saveLikeComment(@RequestParam(value = "userId", required = false) Long userId,
                                   @RequestParam(value = "publishId", required = false) String publishId) {
        // 参数缺失时直接返回，避免 new ObjectId(null) 抛异常（本方法无 try-catch）
        if (userId == null || StringUtils.isBlank(publishId)) {
            return false;
        }

        //判断是否已经点赞，如果已经点赞就返回
        Criteria criteria = Criteria.where("userId").is(userId)
                .and("publishId").is(new ObjectId(publishId))
                .and("commentType").is(1)
                .and("deleted").ne(true);
        Query query = Query.query(criteria);
        long count = this.mongoTemplate.count(query, Comment.class);
        if (count > 0) {
            return false;
        }

        boolean result = this.saveComment(userId, publishId, 1, null, null, null, null);
        if (result) {
            // 评分事件：点赞（type=3）
            Publish publish = this.mongoTemplate.findOne(Query.query(Criteria.where("_id").is(new ObjectId(publishId)).and("deleted").ne(true)), Publish.class);
            this.sendScoringMsg(3, userId, publish);
        }
        return result;
    }

    @Override
    @PostMapping("/removeComment")
    public boolean removeComment(@RequestParam(value = "userId", required = false) Long userId,
                                 @RequestParam(value = "publishId", required = false) String publishId,
                                 @RequestParam(value = "commentType", required = false) Integer commentType) {
        // 参数缺失时直接返回，避免 new ObjectId(null) 抛异常（本方法无 try-catch）
        if (userId == null || StringUtils.isBlank(publishId) || commentType == null) {
            return false;
        }

        Criteria criteria = Criteria.where("userId").is(userId)
                .and("publishId").is(new ObjectId(publishId))
                .and("commentType").is(commentType);
        Query query = Query.query(criteria);

        // 软删除：标记 deleted=true 替代物理删除
        UpdateResult updateResult = this.mongoTemplate.updateMulti(query, Update.update("deleted", true), Comment.class);
        boolean result = updateResult.getModifiedCount() > 0;
        if (result) {
            // 评分事件：取消点赞（commentType=1→type=6）/ 取消喜欢（commentType=3→type=7）
            // commentType 为 Integer 可能为 null，用 equals 避免拆箱 NPE
            Integer scoringType = Integer.valueOf(1).equals(commentType) ? 6
                    : Integer.valueOf(3).equals(commentType) ? 7 : null;
            if (scoringType != null) {
                Publish publish = this.mongoTemplate.findOne(Query.query(Criteria.where("_id").is(new ObjectId(publishId)).and("deleted").ne(true)), Publish.class);
                this.sendScoringMsg(scoringType, userId, publish);
            }
        }
        return result;
    }

    @Override
    @PostMapping("/saveLoveComment")
    public boolean saveLoveComment(@RequestParam(value = "userId", required = false) Long userId,
                                   @RequestParam(value = "publishId", required = false) String publishId) {
        // 参数缺失时直接返回，避免 new ObjectId(null) 抛异常（本方法无 try-catch）
        if (userId == null || StringUtils.isBlank(publishId)) {
            return false;
        }
        //判断是否已经喜欢，如果已经喜欢就返回
        Criteria criteria = Criteria.where("userId").is(userId)
                .and("publishId").is(new ObjectId(publishId))
                .and("commentType").is(3)
                .and("deleted").ne(true);
        Query query = Query.query(criteria);
        long count = this.mongoTemplate.count(query, Comment.class);
        if (count > 0) {
            return false;
        }

        boolean result = this.saveComment(userId, publishId, 3, null, null, null, null);
        if (result) {
            // 评分事件：喜欢（type=4）
            Publish publish = this.mongoTemplate.findOne(Query.query(Criteria.where("_id").is(new ObjectId(publishId)).and("deleted").ne(true)), Publish.class);
            this.sendScoringMsg(4, userId, publish);
        }
        return result;
    }

    @Override
    @PostMapping("/saveComment")
    public boolean saveComment(@RequestParam(value = "userId", required = false) Long userId,
                               @RequestParam(value = "publishId", required = false) String publishId,
                               @RequestParam(value = "type", required = false) Integer type,
                               @RequestParam(value = "content", required = false) String content,
                               @RequestParam(value = "mediaType", required = false) Integer mediaType,
                               @RequestParam(value = "audioUrl", required = false) String audioUrl,
                               @RequestParam(value = "duration", required = false) Integer duration) {

        try {
            Comment comment = new Comment();
            comment.setContent(content);
            comment.setIsParent(true);
            comment.setCommentType(type);
            comment.setPublishId(new ObjectId(publishId));
            comment.setUserId(userId);
            comment.setId(ObjectId.get());
            comment.setCreated(System.currentTimeMillis());
            // 语音评论扩展字段（mediaType=2 时 audioUrl/duration 有值；文字评论保持 null，Mongo 兼容旧数据）
            comment.setMediaType(mediaType);
            comment.setAudioUrl(audioUrl);
            comment.setDuration(duration);

            // 设置发布人的id（软删除过滤：已删除内容不允许评论）
            Publish publish = this.mongoTemplate.findOne(Query.query(Criteria.where("_id").is(comment.getPublishId()).and("deleted").ne(true)), Publish.class);
            if (null != publish) {
                comment.setPublishUserId(publish.getUserId());
            } else {
                Video video = this.mongoTemplate.findOne(Query.query(Criteria.where("_id").is(comment.getPublishId()).and("deleted").ne(true)), Video.class);
                if (null != video) {
                    comment.setPublishUserId(video.getUserId());
                }
            }

            this.mongoTemplate.save(comment);

            // 评分事件：评论（type=5），仅文字评论（commentType=2）
            // 点赞/喜欢由 saveLikeComment/saveLoveComment 各自发射，此处不重复
            // type 为 Integer 可能为 null，用 equals 避免拆箱 NPE
            if (Integer.valueOf(2).equals(type)) {
                this.sendScoringMsg(5, userId, publish);
            }

            return true;
        } catch (Exception e) {
            LOGGER.error("圈子服务处理异常", e);
        }

        return false;
    }

    @Override
    @PostMapping("/saveReplyComment")
    public boolean saveReplyComment(@RequestParam(value = "userId", required = false) Long userId,
                                    @RequestParam(value = "publishId", required = false) String publishId,
                                    @RequestParam(value = "parentId", required = false) String parentId,
                                    @RequestParam(value = "content", required = false) String content) {
        try {
            // 软删除过滤：已删除评论不允许回复
            Comment parent = this.mongoTemplate.findOne(Query.query(Criteria.where("_id").is(new ObjectId(parentId)).and("deleted").ne(true)), Comment.class);
            if (parent == null) {
                return false;
            }
            // 归一到根评论：回复"回复"时统一挂到顶层，保持两级结构
            ObjectId rootId = Boolean.TRUE.equals(parent.getIsParent())
                    ? parent.getId() : parent.getParentId();
            if (rootId == null) {
                return false;
            }

            Comment reply = new Comment();
            reply.setContent(content);
            reply.setIsParent(false);
            reply.setParentId(rootId);
            reply.setCommentType(2);
            reply.setPublishId(new ObjectId(publishId));
            reply.setUserId(userId);
            reply.setId(ObjectId.get());
            reply.setCreated(System.currentTimeMillis());

            // 设置发布人的id（软删除过滤：已删除内容不允许回复）
            Publish publish = this.mongoTemplate.findOne(Query.query(Criteria.where("_id").is(reply.getPublishId()).and("deleted").ne(true)), Publish.class);
            if (null != publish) {
                reply.setPublishUserId(publish.getUserId());
            } else {
                Video video = this.mongoTemplate.findOne(Query.query(Criteria.where("_id").is(reply.getPublishId()).and("deleted").ne(true)), Video.class);
                if (null != video) {
                    reply.setPublishUserId(video.getUserId());
                }
            }

            this.mongoTemplate.save(reply);
            this.sendScoringMsg(5, userId, publish);
            return true;
        } catch (Exception e) {
            LOGGER.error("圈子服务处理异常", e);
        }

        return false;
    }

    @Override
    @GetMapping("/queryCommentCount")
    public Long queryCommentCount(@RequestParam(value = "publishId", required = false) String publishId,
                                  @RequestParam(value = "type", required = false) Integer type) {
        // 软删除过滤：仅统计未删除评论
        Criteria criteria = Criteria.where("publishId").is(new ObjectId(publishId))
                .and("commentType").is(type)
                .and("deleted").ne(true);
        Query query = Query.query(criteria);
        return this.mongoTemplate.count(query, Comment.class);
    }

    @Override
    @PostMapping("/queryCommentCountBatch")
    public Map<String, Long> queryCommentCountBatch(@RequestBody List<String> publishIds,
                                                    @RequestParam(value = "type", required = false) Integer type) {
        Map<String, Long> result = new HashMap<>();
        if (CollectionUtils.isEmpty(publishIds)) {
            return result;
        }

        List<ObjectId> objectIds = new ArrayList<>();
        for (String pid : publishIds) {
            try {
                objectIds.add(new ObjectId(pid));
            } catch (Exception e) {
                LOGGER.warn("无效的publishId，批量查询时跳过: {}", pid);
            }
        }
        if (objectIds.isEmpty()) {
            return result;
        }

        // 聚合：match(publishId in + commentType) -> group(publishId).count()
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("publishId").in(objectIds)
                        .and("commentType").is(type)
                        .and("deleted").ne(true)),
                Aggregation.group("publishId").count().as("count")
        );

        AggregationResults<Document> aggResults =
                this.mongoTemplate.aggregate(agg, "quanzi_comment", Document.class);
        for (Document doc : aggResults.getMappedResults()) {
            Object idObj = doc.get("_id");
            if (idObj instanceof ObjectId) {
                result.put(((ObjectId) idObj).toHexString(), ((Number) doc.get("count")).longValue());
            }
        }

        return result;
    }

    @Override
    @GetMapping("/queryPublishById")
    public Publish queryPublishById(@RequestParam(value = "publishId", required = false) String publishId) {
        // 软删除过滤：已删除动态对 C 端不可见
        return this.mongoTemplate.findOne(Query.query(Criteria.where("_id").is(new ObjectId(publishId)).and("deleted").ne(true)), Publish.class);
    }

    @Override
    @GetMapping("/queryCommentById")
    public Comment queryCommentById(@RequestParam(value = "commentId", required = false) String commentId) {
        // 软删除过滤：已删除评论对 C 端不可见
        return this.mongoTemplate.findOne(Query.query(Criteria.where("_id").is(new ObjectId(commentId)).and("deleted").ne(true)), Comment.class);
    }

    @Override
    @PostMapping("/queryCommentByIdBatch")
    public Map<String, Comment> queryCommentByIdBatch(@RequestBody List<String> commentIds) {
        if (CollectionUtils.isEmpty(commentIds)) {
            return new HashMap<>();
        }
        List<ObjectId> objectIds = new ArrayList<>();
        for (String cid : commentIds) {
            try {
                objectIds.add(new ObjectId(cid));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("queryCommentByIdBatch 非法的 commentId，跳过: {}", cid);
            }
        }
        if (objectIds.isEmpty()) {
            return new HashMap<>();
        }
        // $in 批量查询，一次往返替代 N 次 findById
        List<Comment> comments = this.mongoTemplate.find(
                Query.query(Criteria.where("id").in(objectIds)), Comment.class);
        Map<String, Comment> result = new HashMap<>();
        for (Comment c : comments) {
            if (c.getId() != null) {
                result.put(c.getId().toHexString(), c);
            }
        }
        return result;
    }

    @Override
    @GetMapping("/queryCommentList")
    public PageInfo<Comment> queryCommentList(@RequestParam(value = "publishId", required = false) String publishId,
                                              @RequestParam(value = "page", required = false) Integer page,
                                              @RequestParam(value = "pageSize", required = false) Integer pageSize) {

        int p = safePage(page);
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        PageRequest pageRequest = PageRequest.of(p - 1, size, Sort.by(Sort.Order.asc("created")));

        // 软删除过滤：仅查未删除评论
        Query query = Query.query(Criteria.where("publishId").is(new ObjectId(publishId))
                .and("commentType").is(2)
                .and("deleted").ne(true)).with(pageRequest);

        List<Comment> commentList = this.mongoTemplate.find(query, Comment.class);

        PageInfo<Comment> pageInfo = new PageInfo<>();
        long total = this.mongoTemplate.count(Query.query(Criteria.where("publishId").is(new ObjectId(publishId))
                .and("commentType").is(2)
                .and("deleted").ne(true)), Comment.class);
        pageInfo.setTotal((int) total);
        pageInfo.setPages(size > 0 ? (int) ((total + size - 1) / size) : 0);
        pageInfo.setPageSize(size);
        pageInfo.setPageNum(p);
        pageInfo.setRecords(commentList);

        return pageInfo;
    }

    @Override
    @PostMapping("/queryPublishByPids")
    public List<Publish> queryPublishByPids(@RequestBody List<Long> pids) {
        // 软删除过滤：仅查未删除动态
        Query query = Query.query(Criteria.where("pid").in(pids).and("deleted").ne(true));
        return this.mongoTemplate.find(query, Publish.class);
    }

    @Override
    @GetMapping("/queryCommentListByUser")
    public PageInfo<Comment> queryCommentListByUser(@RequestParam(value = "userId", required = false) Long userId,
                                                    @RequestParam(value = "type", required = false) Integer type,
                                                    @RequestParam(value = "page", required = false) Integer page,
                                                    @RequestParam(value = "pageSize", required = false) Integer pageSize) {

        int p = safePage(page);
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        PageRequest pageRequest = PageRequest.of(p - 1, size, Sort.by(Sort.Order.desc("created")));
        // 软删除过滤：仅查未删除评论
        Query query = new Query(Criteria
                .where("publishUserId").is(userId)
                .and("commentType").is(type)
                .and("deleted").ne(true)).with(pageRequest);

        List<Comment> commentList = this.mongoTemplate.find(query, Comment.class);

        PageInfo<Comment> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(p);
        pageInfo.setPageSize(size);
        pageInfo.setRecords(commentList);
        // count 优化：全量 count 在 200 万文档 + 大V用户场景下扫全部索引记录，撞穿 BFF 5s 读超时导致 503。
        // 改为「find + limit」估算：MAX_TOTAL 是前端分页有意义的上限（100 页 × 10 条），
        // 超过后 total 会大于实际值，前端分页器多显示几页但点击后自然终止，不影响核心功能。
        long total = this.countWithCap(userId, type);
        pageInfo.setTotal((int) total);
        pageInfo.setPages(size > 0 ? (int) ((total + size - 1) / size) : 0);
        return pageInfo;
    }

    /** count 上限：前端分页超过 100 页无意义，超过后不再全量扫描 */
    private static final int MAX_TOTAL = 1000;

    /**
     * 估算总数：find 前 MAX_TOTAL+1 条，超过则返回 MAX_TOTAL。
     * 避免全量 count 在大数据量用户下超时。
     */
    private long countWithCap(Long userId, Integer type) {
        Query countQuery = new Query(Criteria
                .where("publishUserId").is(userId)
                .and("commentType").is(type)
                .and("deleted").ne(true)).limit(MAX_TOTAL + 1);
        List<Comment> capped = this.mongoTemplate.find(countQuery, Comment.class);
        return Math.min(capped.size(), MAX_TOTAL);
    }

    @Override
    @GetMapping("/queryAlbumList")
    public PageInfo<Publish> queryAlbumList(@RequestParam(value = "userId", required = false) Long userId,
                                            @RequestParam(value = "page", required = false) Integer page,
                                            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        int p = safePage(page);
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        PageInfo<Publish> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(p);
        pageInfo.setPageSize(size);
        // 软删除过滤：相册总数仅统计未删除记录
        long total = this.mongoTemplate.count(new Query(Criteria.where("deleted").ne(true)), Album.class, "quanzi_album_" + userId);
        pageInfo.setTotal((int) total);
        pageInfo.setPages(size > 0 ? (int) ((total + size - 1) / size) : 0);

        Pageable pageable = PageRequest.of(p - 1, size, Sort.by(Sort.Order.desc("created")));
        // 软删除过滤：相册仅查未删除记录
        Query query = new Query(Criteria.where("deleted").ne(true)).with(pageable);
        List<Album> albumList = this.mongoTemplate.find(query, Album.class, "quanzi_album_" + userId);

        if(CollectionUtils.isEmpty(albumList)){
            return pageInfo;
        }

        //查询相册所对应的动态信息
        List<ObjectId> publishIds = new ArrayList<>();
        for (Album album : albumList) {
            publishIds.add(album.getPublishId());
        }

        // 软删除过滤：动态仅查未删除记录
        Query publishQuery = Query.query(Criteria.where("id").in(publishIds).and("deleted").ne(true)).with(Sort.by(Sort.Order.desc("created")));
        List<Publish> publishList = this.mongoTemplate.find(publishQuery, Publish.class);

        pageInfo.setRecords(publishList);
        return pageInfo;
    }

    @Override
    @PostMapping("/removePublish")
    public boolean removePublish(@RequestParam(value = "publishId", required = false) String publishId) {
        if (publishId == null || publishId.isEmpty()) {
            return false;
        }
        ObjectId oid;
        try {
            oid = new ObjectId(publishId);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("removePublish 非法的 publishId: {}", publishId);
            return false;
        }

        Publish publish = this.mongoTemplate.findById(oid, Publish.class);
        if (publish == null) {
            return false;
        }

        // 1. 软删除动态主表（标记 deleted=true）
        this.mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(publish.getId())), Update.update("deleted", true), Publish.class);

        // 2. 软删除发布者相册中的记录（失败不阻断，记录告警）
        try {
            Query albumQuery = Query.query(Criteria.where("publishId").is(oid));
            this.mongoTemplate.updateMulti(albumQuery, Update.update("deleted", true), "quanzi_album_" + publish.getUserId());
        } catch (Exception e) {
            LOGGER.error("软删除相册记录失败 publishId={}", publishId, e);
        }

        // 3. 软删除好友时间线中的该动态记录（失败不阻断，记录告警）
        try {
            Query usersQuery = Query.query(Criteria.where("userId").is(publish.getUserId()));
            List<Users> users = this.mongoTemplate.find(usersQuery, Users.class);
            for (Users user : users) {
                Query timeLineQuery = Query.query(Criteria.where("publishId").is(oid));
                this.mongoTemplate.updateMulti(timeLineQuery, Update.update("deleted", true), "quanzi_time_line_" + user.getFriendId());
            }
        } catch (Exception e) {
            LOGGER.error("软删除时间线记录失败 publishId={}", publishId, e);
        }

        LOGGER.info("动态已下架 publishId={} userId={}", publishId, publish.getUserId());
        return true;
    }

    @Override
    @GetMapping("/queryAllPublishList")
    public PageInfo<Publish> queryAllPublishList(@RequestParam(value = "page", required = false) Integer page,
                                                  @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                  @RequestParam(value = "keyword", required = false) String keyword,
                                                  @RequestParam(value = "userId", required = false) Long userId,
                                                  @RequestParam(value = "startTime", required = false) Long startTime,
                                                  @RequestParam(value = "endTime", required = false) Long endTime) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1 || pageSize > 100) ? 10 : pageSize;

        Criteria criteria = new Criteria();
        List<Criteria> conditions = new ArrayList<>();
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

        long total = this.mongoTemplate.count(new Query(criteria), Publish.class);

        Pageable pageable = PageRequest.of(p - 1, size, Sort.by(Sort.Order.desc("created")));
        List<Publish> records = this.mongoTemplate.find(new Query(criteria).with(pageable), Publish.class);

        PageInfo<Publish> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(p);
        pageInfo.setPageSize(size);
        pageInfo.setTotal((int) total);
        pageInfo.setPages(size > 0 ? (int) ((total + size - 1) / size) : 0);
        pageInfo.setRecords(records);
        return pageInfo;
    }

    @Override
    @PostMapping("/recordPublishView")
    public boolean recordPublishView(@RequestParam(value = "userId", required = false) Long userId,
                                     @RequestParam(value = "publishId", required = false) String publishId) {
        // 评分事件：浏览动态（type=2）；软删除过滤：已删除动态不计浏览
        Publish publish = this.mongoTemplate.findOne(Query.query(Criteria.where("_id").is(new ObjectId(publishId)).and("deleted").ne(true)), Publish.class);
        this.sendScoringMsg(2, userId, publish);
        return true;
    }

    @Override
    @GetMapping("/queryAllCommentList")
    public PageInfo<Comment> queryAllCommentList(@RequestParam(value = "page", required = false) Integer page,
                                                  @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                  @RequestParam(value = "keyword", required = false) String keyword,
                                                  @RequestParam(value = "userId", required = false) Long userId,
                                                  @RequestParam(value = "startTime", required = false) Long startTime,
                                                  @RequestParam(value = "endTime", required = false) Long endTime) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1 || pageSize > 100) ? 10 : pageSize;

        // 只查文字评论（commentType=2）
        Criteria criteria = new Criteria();
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(Criteria.where("commentType").is(2));
        if (userId != null) {
            conditions.add(Criteria.where("userId").is(userId));
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            conditions.add(Criteria.where("content").regex(".*" + java.util.regex.Pattern.quote(keyword.trim()) + ".*", "i"));
        }
        if (startTime != null) {
            conditions.add(Criteria.where("created").gte(startTime));
        }
        if (endTime != null) {
            conditions.add(Criteria.where("created").lte(endTime));
        }
        criteria.andOperator(conditions.toArray(new Criteria[0]));

        long total = this.mongoTemplate.count(new Query(criteria), Comment.class);

        Pageable pageable = PageRequest.of(p - 1, size, Sort.by(Sort.Order.desc("created")));
        List<Comment> records = this.mongoTemplate.find(new Query(criteria).with(pageable), Comment.class);

        PageInfo<Comment> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(p);
        pageInfo.setPageSize(size);
        pageInfo.setTotal((int) total);
        pageInfo.setPages(size > 0 ? (int) ((total + size - 1) / size) : 0);
        pageInfo.setRecords(records);
        return pageInfo;
    }

    @Override
    @PostMapping("/removeCommentById")
    public boolean removeCommentById(@RequestParam(value = "commentId", required = false) String commentId) {
        if (commentId == null || commentId.isEmpty()) {
            return false;
        }
        ObjectId oid;
        try {
            oid = new ObjectId(commentId);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("removeCommentById 非法的 commentId: {}", commentId);
            return false;
        }

        Query query = Query.query(Criteria.where("_id").is(oid).and("commentType").is(2));
        // 软删除：标记 deleted=true 替代物理删除
        UpdateResult updateResult = this.mongoTemplate.updateFirst(query, Update.update("deleted", true), Comment.class);
        boolean result = updateResult.getModifiedCount() > 0;
        if (result) {
            LOGGER.info("评论已软删除 commentId={}", commentId);
        }
        return result;
    }

    @Override
    @GetMapping("/countPublishByUser")
    public Long countPublishByUser(@RequestParam(value = "userId", required = false) Long userId) {
        // 软删除过滤：仅统计未删除动态
        Query query = Query.query(Criteria.where("userId").is(userId).and("deleted").ne(true));
        return this.mongoTemplate.count(query, Publish.class);
    }

    @Override
    @GetMapping("/countFollowerByUser")
    public Long countFollowerByUser(@RequestParam(value = "userId", required = false) Long userId) {
        // 软删除过滤：仅统计未删除的粉丝关系
        Query query = Query.query(Criteria.where("followUserId").is(userId).and("deleted").ne(true));
        return this.mongoTemplate.count(query, FollowUser.class);
    }

    @Override
    @GetMapping("/countFollowingByUser")
    public Long countFollowingByUser(@RequestParam(value = "userId", required = false) Long userId) {
        // 软删除过滤：仅统计未删除的关注关系
        Query query = Query.query(Criteria.where("userId").is(userId).and("deleted").ne(true));
        return this.mongoTemplate.count(query, FollowUser.class);
    }

    @Override
    @PostMapping("/countLikeByUser")
    public Long countLikeByUser(@RequestParam(value = "userId", required = false) Long userId) {
        Query query = Query.query(Criteria.where("likeUserId").is(userId));
        return this.mongoTemplate.count(query, UserLike.class);
    }

    @Override
    @PostMapping("/batchRemovePublish")
    public Map<String, Object> batchRemovePublish(@RequestBody List<String> publishIds) {
        if (publishIds == null || publishIds.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("success", 0);
            empty.put("failed", 0);
            return empty;
        }

        int success = 0;
        int failed = 0;
        for (String publishId : publishIds) {
            try {
                if (removePublish(publishId)) {
                    success++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                LOGGER.error("批量删除动态失败 publishId={}", publishId, e);
                failed++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        LOGGER.info("批量删除动态完成: success={}, failed={}", success, failed);
        return result;
    }

    @Override
    @PostMapping("/batchRemoveCommentByIds")
    public Map<String, Object> batchRemoveCommentByIds(@RequestBody List<String> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("success", 0);
            empty.put("failed", 0);
            return empty;
        }

        int success = 0;
        int failed = 0;
        for (String commentId : commentIds) {
            try {
                if (removeCommentById(commentId)) {
                    success++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                LOGGER.error("批量删除评论失败 commentId={}", commentId, e);
                failed++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        LOGGER.info("批量删除评论完成: success={}, failed={}", success, failed);
        return result;
    }

    @Override
    @GetMapping("/searchPublishByTag")
    public PageInfo<Publish> searchPublishByTag(@RequestParam(value = "tag") String tag,
                                                @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        // tags 为数组字段，Criteria.is(tag) 命中数组包含该元素的文档；软删除过滤：仅查未删除动态
        Criteria criteria = Criteria.where("tags").is(tag).and("deleted").ne(true);
        long total = this.mongoTemplate.count(Query.query(criteria), Publish.class);

        int p = safePage(page);
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        Pageable pageable = PageRequest.of(p - 1, size, Sort.by(Sort.Order.desc("created")));
        List<Publish> records = this.mongoTemplate.find(Query.query(criteria).with(pageable), Publish.class);

        PageInfo<Publish> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(p);
        pageInfo.setPageSize(size);
        pageInfo.setTotal((int) total);
        pageInfo.setPages(size > 0 ? (int) ((total + size - 1) / size) : 0);
        pageInfo.setRecords(records);
        return pageInfo;
    }

}
