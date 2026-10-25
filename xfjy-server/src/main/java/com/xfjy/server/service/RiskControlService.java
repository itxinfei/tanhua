package com.xfjy.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 用户级行为频率风控（Redis 固定窗口计数器）
 *
 * <p>在网关 IP 限流（{@code RateLimitGlobalFilter}）之下做第二道用户维度防线，
 * 防单账号高频刷接口。超限返回 false，调用方应响应 429。</p>
 *
 * <p>默认阈值：发布 60s/5次、评论 60s/10次、点赞 60s/20次</p>
 */
@Service
public class RiskControlService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RiskControlService.class);

    /** 行为类型：发布动态/视频 */
    public static final String ACTION_PUBLISH = "PUBLISH";
    /** 行为类型：评论 */
    public static final String ACTION_COMMENT = "COMMENT";
    /** 行为类型：点赞/喜欢 */
    public static final String ACTION_LIKE = "LIKE";

    private static final String KEY_PREFIX = "XFJY_RISK_";
    private static final Duration WINDOW = Duration.ofSeconds(60);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 检查用户行为是否超限
     *
     * @param userId 用户ID
     * @param action 行为类型（ACTION_PUBLISH/ACTION_COMMENT/ACTION_LIKE）
     * @return true=允许，false=超限（调用方应返回 429）
     */
    public boolean allow(Long userId, String action) {
        if (userId == null) {
            return true;
        }
        int limit = resolveLimit(action);
        String key = KEY_PREFIX + action + "_" + userId;
        try {
            Long count = this.redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                this.redisTemplate.expire(key, WINDOW);
            }
            return count == null || count <= limit;
        } catch (Exception e) {
            // Redis 不可用时降级放行，不阻断业务主流程
            LOGGER.error("风控计数失败 userId={} action={}", userId, action, e);
            return true;
        }
    }

    /**
     * 各行为类型的窗口内阈值
     */
    private int resolveLimit(String action) {
        return switch (action) {
            case ACTION_PUBLISH -> 5;
            case ACTION_COMMENT -> 10;
            case ACTION_LIKE -> 20;
            default -> 30;
        };
    }
}