package com.xfjy.service.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权益校验结果（P2）：VIP 等级是否达标 + 每日限次权益的剩余次数
 */
@Data
@NoArgsConstructor
public class BenefitCheckVo implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 权益码 */
    private String serviceCode;
    /** 权益名称 */
    private String name;
    /** 所需 VIP 等级 */
    private Integer vipLevelRequired;
    /** 当前用户 VIP 等级 */
    private Integer userVipLevel;
    /** 是否拥有该权益 */
    private Boolean hasBenefit;
    /** 每日次数限制（不限次权益为 null） */
    private Integer dailyLimit;
    /** 今日已用次数 */
    private Integer usedToday;
    /** 今日剩余次数 */
    private Integer remainingToday;
}