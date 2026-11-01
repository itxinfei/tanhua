package com.xfjy.server.exception;

import lombok.Getter;

/**
 * 权益次数耗尽异常（HTTP 402 PAYMENT_REQUIRED）
 *
 * <p>super_like / advanced_filter 等每日限次权益用完时抛出。</p>
 */
@Getter
public class BenefitLimitException extends RuntimeException {

    /** 402 - 需要付费/升级 */
    public static final int CODE = 402;

    private final String serviceCode;
    private final String serviceName;
    private final String upgradeUrl;

    public BenefitLimitException(String serviceCode, String serviceName, String upgradeUrl) {
        super(String.format("今日「%s」次数已用完，开通 VIP 解锁更多", serviceName));
        this.serviceCode = serviceCode;
        this.serviceName = serviceName;
        this.upgradeUrl = upgradeUrl;
    }
}
