package com.xfjy.user.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 内部服务鉴权拦截器 — 校验 X-Internal-Token 请求头。
 * <p>
 * 白名单由 application.yml 中 internal.auth.tokens 配置，支持逗号分隔多 Token 与热更新。
 */
@Component
public class XInternalTokenInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(XInternalTokenInterceptor.class);

    private static final String HEADER = "X-Internal-Token";

    private final Set<String> validTokens;

    public XInternalTokenInterceptor(@Value("${internal.auth.tokens:}") String tokens) {
        this.validTokens = tokens == null || tokens.isBlank()
                ? Set.of()
                : Set.of(tokens.split(","));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader(HEADER);
        if (token != null && validTokens.contains(token)) {
            return true;
        }
        log.warn("[XInternalToken] 非法调用 URI={}, token={}, IP={}", 
                request.getRequestURI(), token, request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"Forbidden: invalid internal token\"}");
        return false;
    }
}
