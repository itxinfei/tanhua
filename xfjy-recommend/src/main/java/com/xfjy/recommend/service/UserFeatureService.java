package com.xfjy.recommend.service;

import com.xfjy.recommend.pojo.UserFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * 用户特征宽表服务：行为到达时增量 upsert（$inc 计数 + $set 活跃时间）
 * 双塔用户塔训练时直接读 user_feature 集合，无需跨日聚合。
 */
@Service
public class UserFeatureService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserFeatureService.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 记录一条行为到特征宽表（增量更新）
     *
     * @param userId    行为发起者
     * @param actionKey 行为类型键（如 "quanzi:3" 圈子点赞 / "video:2" 视频点赞）
     * @param score     行为得分（取消类为负）
     * @param date      事件时间戳
     */
    public void recordBehavior(Long userId, String actionKey, double score, Long date) {
        try {
            Query query = Query.query(Criteria.where("userId").is(userId));
            Update update = new Update()
                    .inc("actionCounts." + actionKey, 1)
                    .inc("totalScore", score)
                    .inc("behaviorCount", 1)
                    .set("lastActiveTime", date)
                    .set("updated", System.currentTimeMillis());
            this.mongoTemplate.upsert(query, update, UserFeature.class);
        } catch (Exception e) {
            // 特征宽表写入失败不阻断评分主链路
            LOGGER.error("特征宽表更新失败, userId={}, actionKey={}", userId, actionKey, e);
        }
    }
}