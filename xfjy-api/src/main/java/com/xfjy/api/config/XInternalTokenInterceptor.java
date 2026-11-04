package com.xfjy.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 内部服务鉴权拦截器 — 校验 {@code X-Internal-Token} 请求头。
 *
 * <p>白名单由 {@code application.yml} 的 {@code internal.auth.tokens} 配置，支持逗号分隔多 Token。
 * 所有领域服务（circle/video/message/user/match/pay/recommend/elasticsearch）共用此实现，
 * 由各服务 pom 依赖 {@code xfjy-api} 后通过自动装配生效，不再各服务维护副本
 * （原 8 份逐字节等价，2026-11-04 收敛）。</p>
 */
public class XInternalTokenInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(XInternalTokenInterceptor.class);

    private static final String HEADER = "X-Internal-Token";

    private final Set<String> validTokens;

    public XInternalTokenInterceptor(@Value("${internal.auth.tokens:}") String tokens) {
        this.validTokens = tokens == null || tokens.isBlank()
                ? Set.of()
                : Arrays.stream(tokens.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
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
