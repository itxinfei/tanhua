package com.xfjy.recommend.msg;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.xfjy.service.pojo.Publish;
import com.xfjy.recommend.pojo.RecommendQuanZi;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.util.CollectionUtils;

import java.time.Duration;


@Component
@RocketMQMessageListener(topic = "xfjy-quanzi",
        consumerGroup = "xfjy-quanzi-consumer")
public class QuanZiMsgConsumer implements RocketMQListener<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuanZiMsgConsumer.class);

    private static final JsonMapper MAPPER = new JsonMapper();

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(String msg) {
        try {

            JsonNode jsonNode = MAPPER.readTree(msg);
            int type = jsonNode.get("type").asInt();
            String publishId = jsonNode.get("publishId").asText();
            Long date = jsonNode.get("date").asLong();
            Long userId = jsonNode.get("userId").asLong();
            Long pid = jsonNode.get("pid").asLong();

            // 幂等去重：同一事件（publishId+userId+type+date）只处理一次，TTL 24h
            String dedupKey = "mq:dedup:quanzi:" + publishId + ":" + userId + ":" + type + ":" + date;
            Boolean first = this.stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "1", Duration.ofHours(24));
            if (Boolean.FALSE.equals(first)) {
                LOGGER.info("圈子评分重复消息已忽略, key={}", dedupKey);
                return;
            }

            RecommendQuanZi recommendQuanZi = new RecommendQuanZi();
            recommendQuanZi.setPublishId(pid);
            recommendQuanZi.setDate(date);
            recommendQuanZi.setId(ObjectId.get());
            recommendQuanZi.setUserId(userId);

            //1-发动态，2-浏览动态， 3-点赞，4-喜欢，5-评论，6-取消点赞，7-取消喜欢

            switch (type) {
                case 1: {
                    int score = 0;
                    Publish publish = this.mongoTemplate.findById(new ObjectId(publishId), Publish.class);
                    int length = StringUtils.length(publish.getText());

                    if (length > 0 && length <= 50) {
                        score = 1;
                    } else if (length > 50 && length <= 100) {
                        score = 2;
                    } else {
                        score = 3;
                    }

                    if (!CollectionUtils.isEmpty(publish.getMedias())) {
                        score += publish.getMedias().size();
                    }

                    recommendQuanZi.setScore(Double.valueOf(score));

                    break;
                }
                case 2: {
                    recommendQuanZi.setScore(1d);
                    break;
                }
                case 3: {
                    recommendQuanZi.setScore(5d);
                    break;
                }
                case 4: {
                    recommendQuanZi.setScore(8d);
                    break;
                }
                case 5: {
                    recommendQuanZi.setScore(10d);
                    break;
                }
                case 6: {
                    recommendQuanZi.setScore(-5d);
                    break;
                }
                case 7: {
                    recommendQuanZi.setScore(-8d);
                    break;
                }
                default: {
                    recommendQuanZi.setScore(0d);
                    break;
                }

            }

            // 将数据写入到MongoDB
            String collectName = "recommend_quanzi_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            this.mongoTemplate.save(recommendQuanZi, collectName);

        } catch (Exception e) {
            LOGGER.error("消息处理失败! msg = " + msg);
        }

    }
}
