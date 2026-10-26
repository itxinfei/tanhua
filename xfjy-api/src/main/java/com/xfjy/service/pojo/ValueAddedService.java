package com.xfjy.service.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 增值服务权益定义（P2 商业化权益矩阵）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_value_added_service")
public class ValueAddedService implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 权益码（唯一：super_like/see_who_likes/unlimited_swipe/stealth_visit/exclusive_service） */
    private String serviceCode;
    /** 权益名称 */
    private String name;
    /** 所需 VIP 等级（1月卡/2季卡/3年卡） */
    private Integer vipLevelRequired;
    /** 每日次数限制（NULL=不限次） */
    private Integer dailyLimit;
    /** 权益描述 */
    private String description;
    /** 状态：1-上架，0-下架 */
    private Integer status;
    /** 排序权重，越小越靠前 */
    private Integer sort;
    private Date created;
    private Date updated;
}