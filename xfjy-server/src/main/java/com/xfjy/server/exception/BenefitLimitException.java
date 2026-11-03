package com.xfjy.server.exception;

import lombok.Getter;

/**
 * 权益次数耗尽异常（HTTP 402 PAYMENT_REQUIRED）
 *
 * <p>super_like / advanced_filter 等每日限次权益用完时抛出。</p>
 * <p>区分两种场景：未开通 VIP（hasBenefit=false）vs 今日次数已用完（remainingToday<=0）。</p>
 */
@Getter
public class BenefitLimitException extends RuntimeException {

    /** 402 - 需要付费/升级 */
    public static final int CODE = 402;

    private final String serviceCode;
    private final String serviceName;
    private final String upgradeUrl;

    /**
     * 未开通 VIP 场景（hasBenefit=false）
     */
    public BenefitLimitException(String serviceCode, String serviceName, String upgradeUrl) {
        super(String.format("「%s」为 VIP 专属权益，开通 VIP 即可使用", serviceName));
        this.serviceCode = serviceCode;
        this.serviceName = serviceName;
        this.upgradeUrl = upgradeUrl;
    }

    /**
     * 今日次数已用完场景（remainingToday<=0）
     */
    public BenefitLimitException(String serviceCode, String serviceName, String upgradeUrl, boolean limitExhausted) {
        super(String.format("今日「%s」次数已用完，开通 VIP 解锁更多", serviceName));
        this.serviceCode = serviceCode;
        this.serviceName = serviceName;
        this.upgradeUrl = upgradeUrl;
    }
}
