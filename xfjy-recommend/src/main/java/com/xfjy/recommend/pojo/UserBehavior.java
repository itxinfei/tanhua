package com.xfjy.recommend.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 统一行为明细（双塔训练数据源，冗余于按日评分集合）
 * targetType: 1=动态 2=视频；actionType 沿用 MQ 消息 type
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_behavior")
public class UserBehavior {

    @Id
    private ObjectId id;

    @Indexed
    private Long userId;       // 行为发起者

    private Long targetId;     // 目标ID（动态pid / 视频vid）

    private Integer targetType; // 1=动态 2=视频

    private Integer actionType; // 动作类型（沿用 MQ 消息 type）

    private Double score;      // 行为得分

    private Long date;         // 事件时间戳
}