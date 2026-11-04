package com.xfjy.server.interceptor;

import com.xfjy.server.pojo.User;
import com.xfjy.server.service.UserService;
import com.xfjy.server.utils.NoAuthorization;
import com.xfjy.server.utils.UserThreadLocal;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TokenInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenInterceptor.class);

    /** 设备指纹 Redis key 前缀，value 为该用户使用过的 deviceId 集合 */
    private static final String DEVICE_KEY_PREFIX = "XFJY_USER_DEVICE_";

    /** 在线用户 Redis ZSET key，score=最后活跃时间戳(ms)，member=userId */
    private static final String ONLINE_KEY = "XFJY_ONLINE_USERS";

    /** 在线用户心跳有效期：5 分钟无请求视为离线 */
    private static final long ONLINE_TTL_MS = 5 * 60 * 1000L;

    /** 日活用户 Redis SET key 前缀（XFJY_ACTIVE_yyyyMMdd），member=userId，看板 DAU/留存唯一数据源 */
    private static final String ACTIVE_KEY_PREFIX = "XFJY_ACTIVE_";

    /** 日活 SET 保留 35 天：覆盖 30 日留存计算窗口 */
    private static final java.time.Duration ACTIVE_TTL = java.time.Duration.ofDays(35);

    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //判断，请求的方法是否包含@NoAuthorization ，如果包含了，就不需要做处理
        if(handler instanceof HandlerMethod){
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            NoAuthorization annotation = handlerMethod.getMethod().getAnnotation(NoAuthorization.class);
            if(annotation != null){
                return true;
            }
        }

        String token = request.getHeader("Authorization");
        // 兼容标准 Bearer 前缀：前端发 "Bearer xxx"，Redis 中存的是裸 token 值
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        User user = this.userService.queryUserByToken(token);
        if(null == user){
            response.setStatus(401); //无权限
            return false;
        }

        // 存储到当前的线程中
        UserThreadLocal.set(user);

        // 设备指纹记录：header 携带 deviceId 时写入用户设备集合（本期仅记录，用于风控分析，不阻断）
        recordDeviceId(user.getId(), request.getHeader("deviceId"));

        // 在线用户心跳：更新 ZSET 中该用户的最后活跃时间戳（用于看板在线统计）
        recordOnlineHeartbeat(user.getId());

        // 日活埋点：写入当日活跃用户 SET（看板 DAU 趋势与次日/3/7/30 日留存的唯一数据源）
        recordDailyActive(user.getId());
        return true;
    }

    /**
     * 记录用户设备指纹（userId → deviceId 集合），失败不阻断请求
     */
    private void recordDeviceId(Long userId, String deviceId) {
        if (userId == null || StringUtils.isBlank(deviceId)) {
            return;
        }
        try {
            this.redisTemplate.opsForSet().add(DEVICE_KEY_PREFIX + userId, deviceId);
        } catch (Exception e) {
            LOGGER.warn("设备指纹记录失败 userId={} deviceId={}", userId, deviceId, e);
        }
    }

    /**
     * 在线用户心跳：将用户ID写入 ZSET（score=当前时间戳），
     * 5 分钟无请求的用户在看板统计时被过滤掉，视为离线
     */
    private void recordOnlineHeartbeat(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            this.redisTemplate.opsForZSet().add(ONLINE_KEY, String.valueOf(userId), System.currentTimeMillis());
        } catch (Exception e) {
            LOGGER.warn("在线心跳记录失败 userId={}", userId, e);
        }
    }

    /**
     * 日活埋点：把用户写入当日活跃 SET。
     * 仅当该用户当天首次出现（SADD 返回 1）时刷新 TTL，把每请求开销控制为 1 次 SADD；
     * Redis 故障只记日志，不阻断请求。
     */
    private void recordDailyActive(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            String key = ACTIVE_KEY_PREFIX + java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            Long added = this.redisTemplate.opsForSet().add(key, String.valueOf(userId));
            if (added != null && added > 0) {
                this.redisTemplate.expire(key, ACTIVE_TTL);
            }
        } catch (Exception e) {
            LOGGER.warn("日活埋点记录失败 userId={}", userId, e);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理ThreadLocal，防止线程池复用导致数据串号
        UserThreadLocal.remove();
    }
}
