package com.xfjy.service.api;

import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.mongodb.client.result.DeleteResult;
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
            LOGGER.error("圈子评分消息发送失败! type = " + type + ", userId = " + userId, e);
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
            this.mongoTemplate.remove(publish);
            return null;
        }

        //第三步：将动态写入好友的时间线表（失败则补偿回滚相册+动态+已写入的时间线）
        Query query = Query.query(Criteria.where("userId").is(publish.getUserId()));
        List<Users> users = this.mongoTemplate.find(query, Users.class);

        //记录已写入的时间线（集合名与记录id），用于补偿回滚
        List<ObjectId> savedTimeLineIds = new ArrayList<>();
        List<String> savedTimeLineCollections = new ArrayList<>();

        try {
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
            //回滚已写入的时间线
            for (int i = 0; i < savedTimeLineIds.size(); i++) {
                this.mongoTemplate.remove(
                        Query.query(Criteria.where("id").is(savedTimeLineIds.get(i))),
                        TimeLine.class, savedTimeLineCollections.get(i));
            }
            //回滚相册
            this.mongoTemplate.remove(
                    Query.query(Criteria.where("id").is(album.getId())),
                    Album.class, "quanzi_album_" + publish.getUserId());
            //回滚动态
            this.mongoTemplate.remove(publish);
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

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Order.desc("date")));
        Query query = new Query().with(pageable);

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

        Query queryPublish = Query.query(Criteria.where("id").in(ids)).with(Sort.by(Sort.Order.desc("created")));
        //查询动态信息
        List<Publish> publishList = this.mongoTemplate.find(queryPublish, Publish.class);

        // 封装分页对象
        PageInfo<Publish> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(page);
        pageInfo.setPageSize(pageSize);
        pageInfo.setTotal(0); //不提供总数
        pageInfo.setRecords(publishList);

        return pageInfo;
    }

    @Override
    @PostMapping("/saveLikeComment")
    public boolean saveLikeComment(@RequestParam(value = "userId", required = false) Long userId,
                                   @RequestParam(value = "publishId", required = false) String publishId) {

        //判断是否已经点赞，如果已经点赞就返回
        Criteria criteria = Criteria.where("userId").is(userId)
                .and("publishId").is(new ObjectId(publishId))
                .and("commentType").is(1);
        Query query = Query.query(criteria);
        long count = this.mongoTemplate.count(query, Comment.class);
        if (count > 0) {
            return false;
        }

        boolean result = this.saveComment(userId, publishId, 1, null);
        if (result) {
            // 评分事件：点赞（type=3）
            Publish publish = this.mongoTemplate.findById(new ObjectId(publishId), Publish.class);
            this.sendScoringMsg(3, userId, publish);
        }
        return result;
    }

    @Override
    @PostMapping("/removeComment")
    public boolean removeComment(@RequestParam(value = "userId", required = false) Long userId,
                                 @RequestParam(value = "publishId", required = false) String publishId,
                                 @RequestParam(value = "commentType", required = false) Integer commentType) {

        Criteria criteria = Criteria.where("userId").is(userId)
                .and("publishId").is(new ObjectId(publishId))
                .and("commentType").is(commentType);
        Query query = Query.query(criteria);

        DeleteResult deleteResult = this.mongoTemplate.remove(query, Comment.class);
        boolean result = deleteResult.getDeletedCount() > 0;
        if (result) {
            // 评分事件：取消点赞（commentType=1→type=6）/ 取消喜欢（commentType=3→type=7）
            Integer scoringType = (commentType == 1) ? 6 : (commentType == 3) ? 7 : null;
            if (scoringType != null) {
                Publish publish = this.mongoTemplate.findById(new ObjectId(publishId), Publish.class);
                this.sendScoringMsg(scoringType, userId, publish);
            }
        }
        return result;
    }

    @Override
    @PostMapping("/saveLoveComment")
    public boolean saveLoveComment(@RequestParam(value = "userId", required = false) Long userId,
                                   @RequestParam(value = "publishId", required = false) String publishId) {
        //判断是否已经喜欢，如果已经喜欢就返回
        Criteria criteria = Criteria.where("userId").is(userId)
                .and("publishId").is(new ObjectId(publishId))
                .and("commentType").is(3);
        Query query = Query.query(criteria);
        long count = this.mongoTemplate.count(query, Comment.class);
        if (count > 0) {
            return false;
        }

        boolean result = this.saveComment(userId, publishId, 3, null);
        if (result) {
            // 评分事件：喜欢（type=4）
            Publish publish = this.mongoTemplate.findById(new ObjectId(publishId), Publish.class);
            this.sendScoringMsg(4, userId, publish);
        }
        return result;
    }

    @Override
    @PostMapping("/saveComment")
    public boolean saveComment(@RequestParam(value = "userId", required = false) Long userId,
                               @RequestParam(value = "publishId", required = false) String publishId,
                               @RequestParam(value = "type", required = false) Integer type,
                               @RequestParam(value = "content", required = false) String content) {

        try {
            Comment comment = new Comment();
            comment.setContent(content);
            comment.setIsParent(true);
            comment.setCommentType(type);
            comment.setPublishId(new ObjectId(publishId));
            comment.setUserId(userId);
            comment.setId(ObjectId.get());
            comment.setCreated(System.currentTimeMillis());

            // 设置发布人的id
            Publish publish = this.mongoTemplate.findById(comment.getPublishId(), Publish.class);
            if (null != publish) {
                comment.setPublishUserId(publish.getUserId());
            } else {
                Video video = this.mongoTemplate.findById(comment.getPublishId(), Video.class);
                if (null != video) {
                    comment.setPublishUserId(video.getUserId());
                }
            }

            this.mongoTemplate.save(comment);

            // 评分事件：评论（type=5），仅文字评论（commentType=2）
            // 点赞/喜欢由 saveLikeComment/saveLoveComment 各自发射，此处不重复
            if (type == 2) {
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
            Comment parent = this.mongoTemplate.findById(new ObjectId(parentId), Comment.class);
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

            // 设置发布人的id
            Publish publish = this.mongoTemplate.findById(reply.getPublishId(), Publish.class);
            if (null != publish) {
                reply.setPublishUserId(publish.getUserId());
            } else {
                Video video = this.mongoTemplate.findById(reply.getPublishId(), Video.class);
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
        Criteria criteria = Criteria.where("publishId").is(new ObjectId(publishId))
                .and("commentType").is(type);
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
                        .and("commentType").is(type)),
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
        return this.mongoTemplate.findById(new ObjectId(publishId), Publish.class);
    }

    @Override
    @GetMapping("/queryCommentById")
    public Comment queryCommentById(@RequestParam(value = "commentId", required = false) String commentId) {
        return this.mongoTemplate.findById(new ObjectId(commentId), Comment.class);
    }

    @Override
    @GetMapping("/queryCommentList")
    public PageInfo<Comment> queryCommentList(@RequestParam(value = "publishId", required = false) String publishId,
                                              @RequestParam(value = "page", required = false) Integer page,
                                              @RequestParam(value = "pageSize", required = false) Integer pageSize) {

        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Order.asc("created")));

        Query query = Query.query(Criteria.where("publishId").is(new ObjectId(publishId))
                .and("commentType").is(2)).with(pageRequest);

        List<Comment> commentList = this.mongoTemplate.find(query, Comment.class);

        PageInfo<Comment> pageInfo = new PageInfo<>();
        pageInfo.setTotal(0);
        pageInfo.setPageSize(pageSize);
        pageInfo.setPageNum(page);
        pageInfo.setRecords(commentList);

        return pageInfo;
    }

    @Override
    @PostMapping("/queryPublishByPids")
    public List<Publish> queryPublishByPids(@RequestBody List<Long> pids) {
        Query query = Query.query(Criteria.where("pid").in(pids));
        return this.mongoTemplate.find(query, Publish.class);
    }

    @Override
    @GetMapping("/queryCommentListByUser")
    public PageInfo<Comment> queryCommentListByUser(@RequestParam(value = "userId", required = false) Long userId,
                                                    @RequestParam(value = "type", required = false) Integer type,
                                                    @RequestParam(value = "page", required = false) Integer page,
                                                    @RequestParam(value = "pageSize", required = false) Integer pageSize) {

        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Order.desc("created")));
        Query query = new Query(Criteria
                .where("publishUserId").is(userId)
                .and("commentType").is(type)).with(pageRequest);

        List<Comment> commentList = this.mongoTemplate.find(query, Comment.class);

        PageInfo<Comment> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(page);
        pageInfo.setPageSize(pageSize);
        pageInfo.setRecords(commentList);
        pageInfo.setTotal(0); //不提供总数
        return pageInfo;
    }

    @Override
    @GetMapping("/queryAlbumList")
    public PageInfo<Publish> queryAlbumList(@RequestParam(value = "userId", required = false) Long userId,
                                            @RequestParam(value = "page", required = false) Integer page,
                                            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        PageInfo<Publish> pageInfo = new PageInfo<>();
        pageInfo.setPageNum(page);
        pageInfo.setPageSize(pageSize);
        pageInfo.setTotal(0); //不提供总数

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Order.desc("created")));
        Query query = new Query().with(pageable);
        List<Album> albumList = this.mongoTemplate.find(query, Album.class, "quanzi_album_" + userId);

        if(CollectionUtils.isEmpty(albumList)){
            return pageInfo;
        }

        //查询相册所对应的动态信息
        List<ObjectId> publishIds = new ArrayList<>();
        for (Album album : albumList) {
            publishIds.add(album.getPublishId());
        }

        Query publishQuery = Query.query(Criteria.where("id").in(publishIds)).with(Sort.by(Sort.Order.desc("created")));
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

        // 1. 删除动态主表
        this.mongoTemplate.remove(publish);

        // 2. 删除发布者相册中的记录
        Query albumQuery = Query.query(Criteria.where("publishId").is(oid));
        this.mongoTemplate.remove(albumQuery, "quanzi_album_" + publish.getUserId());

        // 3. 删除好友时间线中的该动态记录
        Query usersQuery = Query.query(Criteria.where("userId").is(publish.getUserId()));
        List<Users> users = this.mongoTemplate.find(usersQuery, Users.class);
        for (Users user : users) {
            Query timeLineQuery = Query.query(Criteria.where("publishId").is(oid));
            this.mongoTemplate.remove(timeLineQuery, "quanzi_time_line_" + user.getFriendId());
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
        pageInfo.setRecords(records);
        return pageInfo;
    }

    @Override
    @PostMapping("/recordPublishView")
    public boolean recordPublishView(@RequestParam(value = "userId", required = false) Long userId,
                                     @RequestParam(value = "publishId", required = false) String publishId) {
        // 评分事件：浏览动态（type=2）
        Publish publish = this.mongoTemplate.findById(new ObjectId(publishId), Publish.class);
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
        DeleteResult deleteResult = this.mongoTemplate.remove(query, Comment.class);
        boolean result = deleteResult.getDeletedCount() > 0;
        if (result) {
            LOGGER.info("评论已删除 commentId={}", commentId);
        }
        return result;
    }

    @Override
    @GetMapping("/countPublishByUser")
    public Long countPublishByUser(@RequestParam(value = "userId", required = false) Long userId) {
        Query query = Query.query(Criteria.where("userId").is(userId));
        return this.mongoTemplate.count(query, Publish.class);
    }

    @Override
    @GetMapping("/countFollowerByUser")
    public Long countFollowerByUser(@RequestParam(value = "userId", required = false) Long userId) {
        Query query = Query.query(Criteria.where("followUserId").is(userId));
        return this.mongoTemplate.count(query, FollowUser.class);
    }

    @Override
    @GetMapping("/countFollowingByUser")
    public Long countFollowingByUser(@RequestParam(value = "userId", required = false) Long userId) {
        Query query = Query.query(Criteria.where("userId").is(userId));
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

}
