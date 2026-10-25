package com.xfjy.recommend.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * 用户特征宽表（双塔用户塔特征输入，行为到达时增量 upsert）
 * actionCounts key 形如 "quanzi:3"（圈子点赞）、"video:2"（视频点赞）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_feature")
public class UserFeature {

    @Id
    private ObjectId id;

    @Indexed
    private Long userId;

    private Map<String, Long> actionCounts; // 各行为类型计数

    private Double totalScore;    // 累计行为得分

    private Long behaviorCount;   // 累计行为次数

    private Long lastActiveTime;  // 最近活跃时间戳

    private Long updated;         // 宽表更新时间戳
}