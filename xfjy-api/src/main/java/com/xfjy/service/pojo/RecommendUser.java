package com.xfjy.service.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Document(collection = "recommend_user")
public class RecommendUser implements java.io.Serializable {

    private static final long serialVersionUID = 5874126532504390567L;

    @Id
    private ObjectId id; //主键id
    @Indexed
    private Long userId; //推荐的用户id
    private Long toUserId; //用户id
    @Indexed
    private Double score; //推荐得分
    private String date; //日期

    /**
     * 社交信号分（0~1）：由二度好友召回时按共同好友数归一化写入，仅运行时计算用于混合评分，
     * 不持久化到 MongoDB。非 null 且 >=0 表示当前候选是「朋友的朋友」社交候选，
     * 此时 ALS 协同过滤维度视为缺失（降级），由社交维度（w-social）代替参与排序。
     */
    @Transient
    private Double socialScore;
}
