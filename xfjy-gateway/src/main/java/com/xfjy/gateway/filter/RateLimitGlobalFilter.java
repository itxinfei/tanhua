package com.xfjy.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;

/**
 * 网关 IP 限流全局过滤器（Redis 固定窗口计数器）
 *
 * <p>针对发布类写操作（POST 动态/评论/视频/举报）做 IP 维度限流，
 * 60 秒窗口内同一 IP 最多 5 次，超限返回 429。
 * 用户维度限流由 {@code xfjy-server} 的 RiskControlService 负责，两层互补。</p>
 *
 * <p>Redis 不可用时降级放行，不阻断业务主流程。</p>
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitGlobalFilter.class);

    /** 需要限流的 POST 路径（发布类写操作） */
    private static final Set<String> LIMITED_PATHS = Set.of(
            "/movements", "/comments", "/smallVideos", "/users/report");

    private static final String KEY_PREFIX = "XFJY_GATEWAY_LIMIT_";
    private static final Duration WINDOW = Duration.ofSeconds(60);
    private static final int LIMIT = 5;

    private final ReactiveStringRedisTemplate redisTemplate;

    public RateLimitGlobalFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 仅限 POST 发布类路径
        if (!HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        if (!isLimitedPath(path)) {
            return chain.filter(exchange);
        }

        String ip = resolveClientIp(exchange);
        String key = KEY_PREFIX + ip + "_" + path;

        return this.redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count != null && count == 1L) {
                        return this.redisTemplate.expire(key, WINDOW).then(Mono.just(count));
                    }
                    return Mono.just(count == null ? 0L : count);
                })
                .flatMap(count -> {
                    if (count > LIMIT) {
                        LOGGER.warn("网关限流触发 ip={} path={} count={}", ip, path, count);
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> {
                    LOGGER.error("网关限流计数失败，降级放行 ip={} path={}", ip, path, e);
                    return chain.filter(exchange);
                });
    }

    /**
     * 路径是否命中限流名单（精确匹配或一级子路径）
     */
    private boolean isLimitedPath(String path) {
        for (String limited : LIMITED_PATHS) {
            if (path.equals(limited) || path.startsWith(limited + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析客户端真实 IP：X-Forwarded-For 首段 → X-Real-IP → 远端地址
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    @Override
    public int getOrder() {
        // 高优先级，在路由转发前拦截
        return -100;
    }
}