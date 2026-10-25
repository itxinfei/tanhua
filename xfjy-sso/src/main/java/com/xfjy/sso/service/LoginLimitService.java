package com.xfjy.sso.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录防爆破限流（Redis 计数器）
 *
 * <p>三层防线：
 * <ul>
 *   <li>IP 维度：同 IP 60s 内最多 10 次 登录/发码 请求，防脚本批量撞库</li>
 *   <li>手机号维度：连续登录失败 5 次，锁定该账号 15 分钟</li>
 *   <li>验证码维度：同手机号 60s 内最多发 1 次，防短信轰炸</li>
 * </ul>
 * Redis 不可用时降级放行。</p>
 */
@Service
public class LoginLimitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginLimitService.class);

    private static final String IP_KEY_PREFIX = "XFJY_SSO_LIMIT_IP_";
    private static final String FAIL_KEY_PREFIX = "XFJY_SSO_LOGIN_FAIL_";
    private static final String LOCK_KEY_PREFIX = "XFJY_SSO_LOGIN_LOCK_";
    private static final String CODE_LIMIT_KEY_PREFIX = "XFJY_SSO_CODE_LIMIT_";

    private static final Duration IP_WINDOW = Duration.ofSeconds(60);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration CODE_LIMIT_WINDOW = Duration.ofSeconds(60);

    private static final int IP_LIMIT = 10;
    private static final int FAIL_THRESHOLD = 5;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * IP 维度限流：60s 内最多 10 次
     *
     * @return true=允许，false=超限（调用方应返回 429）
     */
    public boolean allowByIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return true;
        }
        try {
            String key = IP_KEY_PREFIX + ip;
            Long count = this.redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                this.redisTemplate.expire(key, IP_WINDOW);
            }
            return count == null || count <= IP_LIMIT;
        } catch (Exception e) {
            LOGGER.error("IP 限流计数失败，降级放行 ip={}", ip, e);
            return true;
        }
    }

    /**
     * 手机号是否被锁定（连续失败 5 次锁 15 分钟）
     *
     * @return true=允许登录，false=已锁定（调用方应返回 423）
     */
    public boolean allowByPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return true;
        }
        try {
            return !Boolean.TRUE.equals(this.redisTemplate.hasKey(LOCK_KEY_PREFIX + phone));
        } catch (Exception e) {
            LOGGER.error("账号锁定检查失败，降级放行 phone={}", phone, e);
            return true;
        }
    }

    /**
     * 记录登录失败，达到阈值则锁定账号 15 分钟
     */
    public void recordLoginFailure(String phone) {
        if (phone == null || phone.isEmpty()) {
            return;
        }
        try {
            String failKey = FAIL_KEY_PREFIX + phone;
            Long fails = this.redisTemplate.opsForValue().increment(failKey);
            if (fails != null && fails == 1L) {
                // 失败计数 30 分钟后自动过期（无残留）
                this.redisTemplate.expire(failKey, Duration.ofMinutes(30));
            }
            if (fails != null && fails >= FAIL_THRESHOLD) {
                this.redisTemplate.opsForValue().set(LOCK_KEY_PREFIX + phone, "1", LOCK_DURATION);
                this.redisTemplate.delete(failKey);
                LOGGER.warn("账号登录失败达阈值已锁定 phone={} fails={}", phone, fails);
            }
        } catch (Exception e) {
            LOGGER.error("登录失败计数异常 phone={}", phone, e);
        }
    }

    /**
     * 登录成功，清除失败计数
     */
    public void recordLoginSuccess(String phone) {
        if (phone == null || phone.isEmpty()) {
            return;
        }
        try {
            this.redisTemplate.delete(FAIL_KEY_PREFIX + phone);
        } catch (Exception e) {
            LOGGER.error("清除登录失败计数异常 phone={}", phone, e);
        }
    }

    /**
     * 验证码发送限流：同手机号 60s 内最多 1 次
     *
     * @return true=允许发送，false=超限（调用方应返回 429）
     */
    public boolean allowSendCode(String phone) {
        if (phone == null || phone.isEmpty()) {
            return true;
        }
        try {
            String key = CODE_LIMIT_KEY_PREFIX + phone;
            Long count = this.redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                this.redisTemplate.expire(key, CODE_LIMIT_WINDOW);
            }
            return count != null && count == 1L;
        } catch (Exception e) {
            LOGGER.error("验证码发送限流计数失败，降级放行 phone={}", phone, e);
            return true;
        }
    }
}