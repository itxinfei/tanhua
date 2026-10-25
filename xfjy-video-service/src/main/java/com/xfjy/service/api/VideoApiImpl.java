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
    @PostMapping("/saveVideo")
    public String saveVideo(@RequestBody Video video) {

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
    @GetMapping("/queryVideoList")
    public PageInfo<Video> queryVideoList(@RequestParam("page") Integer page,
                                          @RequestParam("pageSize") Integer pageSize) {
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
    @PostMapping("/followUser")
    public Boolean followUser(@RequestParam("userId") Long userId,
                              @RequestParam("followUserId") Long followUserId) {
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
    @PostMapping("/disFollowUser")
    public Boolean disFollowUser(@RequestParam("userId") Long userId,
                                 @RequestParam("followUserId") Long followUserId) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("followUserId").is(followUserId));
        DeleteResult deleteResult = this.mongoTemplate.remove(query, FollowUser.class);
        return deleteResult.getDeletedCount() > 0;
    }

    @Override
    @GetMapping("/queryVideoById")
    public Video queryVideoById(@RequestParam("id") String id) {
        return this.mongoTemplate.findById(new ObjectId(id), Video.class);
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
}
