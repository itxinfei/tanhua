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

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理ThreadLocal，防止线程池复用导致数据串号
        UserThreadLocal.remove();
    }
}
