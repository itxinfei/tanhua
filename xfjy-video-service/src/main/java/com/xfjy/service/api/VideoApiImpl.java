package com.xfjy.service.api;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public String saveVideo(Video video) {

        if (video.getUserId() == null) {
            return null;
        }

        video.setId(ObjectId.get());
        video.setCreated(System.currentTimeMillis());
        //自增长id
        video.setVid(this.idService.createId("video", video.getId().toHexString()));
        this.mongoTemplate.save(video);

        // 评分事件：发布视频（type=1）
        this.sendVideoMsg(video.getUserId(), video.getId().toHexString(), video.getVid(), 1);

        return video.getId().toHexString();
    }

    @Override
    public PageInfo<Video> queryVideoList(Integer page, Integer pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Order.desc("created")));
        Query query = new Query().with(pageable);
        List<Video> videos = this.mongoTemplate.find(query, Video.class);
        PageInfo<Video> pageInfo = new PageInfo<>();
        pageInfo.setRecords(videos);
        pageInfo.setPageNum(page);
        pageInfo.setPageSize(pageSize);
        pageInfo.setTotal(0); //不提供总数
        return pageInfo;
    }

    @Override
    public Boolean followUser(Long userId, Long followUserId) {
        try {
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
    public Boolean disFollowUser(Long userId, Long followUserId) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("followUserId").is(followUserId));
        DeleteResult deleteResult = this.mongoTemplate.remove(query, FollowUser.class);
        return deleteResult.getDeletedCount() > 0;
    }

    @Override
    public Video queryVideoById(String id) {
        return this.mongoTemplate.findById(new ObjectId(id), Video.class);
    }

    @Override
    public List<Video> queryVideoListByPids(List<Long> vids) {
        Query query = Query.query(Criteria.where("vid").in(vids));
        return this.mongoTemplate.find(query, Video.class);
    }

    @Override
    public Boolean recordVideoMsg(Long userId, String videoId, Integer type) {
        Video video = this.mongoTemplate.findById(new ObjectId(videoId), Video.class);
        if (video == null) {
            return false;
        }
        this.sendVideoMsg(userId, videoId, video.getVid(), type);
        return true;
    }
}
