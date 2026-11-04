package com.xfjy.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

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

    /**
     * Lua 脚本：原子 INCR + 首次 EXPIRE。
     * 防止 INCR 后进程崩溃导致 key 永不过期（用户被永久限流）；
     * ARGV[1] 为窗口秒数，仅在计数为 1（首次）时设置过期。
     */
    private static final DefaultRedisScript<Long> INCR_EXPIRE_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end return c",
            Long.class);

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
            // Lua 原子 INCR+EXPIRE：显式 String 序列化 ARGV（模板默认 Jdk 序列化会污染 TTL 参数）
            Long count = this.redisTemplate.execute(INCR_EXPIRE_SCRIPT, RedisSerializer.string(), null,
                    Collections.singletonList(key), String.valueOf(WINDOW.getSeconds()));
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