package com.xfjy.service.api;

import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
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
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

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
    public String savePublish(Publish publish) {

        //校验publish对象
        if (publish.getUserId() == null) {
            return null;
        }
        // ……等其他的逻辑类似

        try {
            //填充数据
            publish.setId(ObjectId.get());
            publish.setCreated(System.currentTimeMillis()); //发布时间
            publish.setSeeType(1); //查看权限

            //增加自增长的pid
            publish.setPid(this.idService.createId("publish", publish.getId().toHexString()));

            //保存动态信息
            this.mongoTemplate.save(publish);

            //写入到自己的相册表中
            Album album = new Album();
            album.setId(ObjectId.get());
            album.setPublishId(publish.getId());//动态id
            album.setCreated(System.currentTimeMillis());

            //将相册对象写入到MongoDB??
            this.mongoTemplate.save(album, "quanzi_album_" + publish.getUserId());

            //查询当前用户的好友数据，将动态数据写入到好友的时间线表中
            Query query = Query.query(Criteria.where("userId").is(publish.getUserId()));
            List<Users> users = this.mongoTemplate.find(query, Users.class);
            for (Users user : users) {

                TimeLine timeLine = new TimeLine();
                timeLine.setId(ObjectId.get());
                timeLine.setUserId(publish.getUserId());
                timeLine.setPublishId(publish.getId());
                timeLine.setDate(System.currentTimeMillis());

                this.mongoTemplate.save(timeLine, "quanzi_time_line_" + user.getFriendId());

            }

            // 评分事件：发动态（type=1）
            this.sendScoringMsg(1, publish.getUserId(), publish);

            return publish.getId().toHexString();
        } catch (Exception e) {
            LOGGER.error("圈子服务处理异常", e);
            //TODO 事务回滚
        }

        return null;
    }

    @Override
    public PageInfo<Publish> queryPublishList(Long userId, Integer page, Integer pageSize) {

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
    public boolean saveLikeComment(Long userId, String publishId) {

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
    public boolean removeComment(Long userId, String publishId, Integer commentType) {

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
    public boolean saveLoveComment(Long userId, String publishId) {
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
    public boolean saveComment(Long userId, String publishId, Integer type, String content) {

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
    public Long queryCommentCount(String publishId, Integer type) {
        Criteria criteria = Criteria.where("publishId").is(new ObjectId(publishId))
                .and("commentType").is(type);
        Query query = Query.query(criteria);
        return this.mongoTemplate.count(query, Comment.class);
    }

    @Override
    public Publish queryPublishById(String publishId) {
        return this.mongoTemplate.findById(new ObjectId(publishId), Publish.class);
    }

    @Override
    public PageInfo<Comment> queryCommentList(String publishId, Integer page, Integer pageSize) {

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
    public List<Publish> queryPublishByPids(List<Long> pids) {
        Query query = Query.query(Criteria.where("pid").in(pids));
        return this.mongoTemplate.find(query, Publish.class);
    }

    @Override
    public PageInfo<Comment> queryCommentListByUser(Long userId, Integer type, Integer page, Integer pageSize) {

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
    public PageInfo<Publish> queryAlbumList(Long userId, Integer page, Integer pageSize) {
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
    public boolean recordPublishView(Long userId, String publishId) {
        // 评分事件：浏览动态（type=2）
        Publish publish = this.mongoTemplate.findById(new ObjectId(publishId), Publish.class);
        this.sendScoringMsg(2, userId, publish);
        return true;
    }

}
