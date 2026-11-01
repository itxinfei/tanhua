package com.xfjy.server.exception;

import lombok.Getter;

/**
 * 滑动次数耗尽异常（HTTP 402 PAYMENT_REQUIRED）
 *
 * <p>后端兜底：前端 storage 绕过 → 后端以 tb_swipe_log 实际次数为准触发此异常。</p>
 * <p>由 {@link GlobalExceptionHandler} 捕获后返回 HTTP 402 + 结构化 message（含 upgradeUrl）。</p>
 */
@Getter
public class SwipeLimitException extends RuntimeException {

    /** 402 - 需要付费/升级 */
    public static final int CODE = 402;

    private final String limitType;
    private final int currentUsage;
    private final int maxLimit;
    private final String upgradeUrl;

    public SwipeLimitException(String limitType, int currentUsage, int maxLimit, String upgradeUrl) {
        super(String.format("今日滑动次数已用完（%d/%d），开通 VIP 解锁无限滑动", currentUsage, maxLimit));
        this.limitType = limitType;
        this.currentUsage = currentUsage;
        this.maxLimit = maxLimit;
        this.upgradeUrl = upgradeUrl;
    }
}
