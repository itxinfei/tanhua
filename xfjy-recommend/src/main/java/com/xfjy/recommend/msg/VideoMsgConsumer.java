package com.xfjy.recommend.msg;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.xfjy.recommend.pojo.RecommendVideo;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.bson.types.ObjectId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RocketMQMessageListener(topic = "xfjy-video",
        consumerGroup = "xfjy-video-consumer")
public class VideoMsgConsumer implements RocketMQListener<String> {

    private static final JsonMapper MAPPER = new JsonMapper();

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoMsgConsumer.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(String msg) {
        try {
            JsonNode jsonNode = MAPPER.readTree(msg);

            Long userId = jsonNode.get("userId").asLong();
            Long vid = jsonNode.get("vid").asLong();
            Integer type = jsonNode.get("type").asInt();
            Long date = jsonNode.has("date") ? jsonNode.get("date").asLong() : System.currentTimeMillis();

            // 幂等去重：同一事件（vid+userId+type+date）只处理一次，TTL 24h
            String dedupKey = "mq:dedup:video:" + vid + ":" + userId + ":" + type + ":" + date;
            Boolean first = this.stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofHours(24));
            if (Boolean.FALSE.equals(first)) {
                LOGGER.info("视频评分重复消息已忽略, key={}", dedupKey);
                return;
            }

            //1-发动态，2-点赞，3-取消点赞，4-评论
            RecommendVideo recommendVideo = new RecommendVideo();
            recommendVideo.setUserId(userId);
            recommendVideo.setId(ObjectId.get());
            recommendVideo.setDate(System.currentTimeMillis());
            recommendVideo.setVideoId(vid);

            switch (type) {
                case 1: {
                    recommendVideo.setScore(2d);
                    break;
                }
                case 2: {
                    recommendVideo.setScore(5d);
                    break;
                }
                case 3: {
                    recommendVideo.setScore(-5d);
                    break;
                }
                case 4: {
                    recommendVideo.setScore(10d);
                    break;
                }
                default: {
                    recommendVideo.setScore(0d);
                    break;
                }
            }

            String collectionName = "recommend_video_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            this.mongoTemplate.save(recommendVideo, collectionName);

        } catch (Exception e) {
            LOGGER.error("处理小视频消息失败~" + msg, e);
        }
    }
}
