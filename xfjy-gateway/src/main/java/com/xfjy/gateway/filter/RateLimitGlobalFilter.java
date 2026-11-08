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
import java.util.regex.Pattern;

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

    /** 需要限流的 POST 路径（发布类写操作，精确匹配） */
    private static final Set<String> LIMITED_PATHS = Set.of(
            "/movements", "/comments", "/small-videos", "/users/report");

    /**
     * 视频评论发布路径模式：/small-videos/{id}/comments。
     * 与 {@code /comments} 同为发布类写操作，但挂在 /small-videos 前缀下，需单独匹配。
     * 注意：仅匹配此模式，不匹配 /play、/like、/dislike、/userFocus 等高频互动子路径。
     */
    private static final Pattern VIDEO_COMMENT_PATTERN =
            Pattern.compile("^/small-videos/[^/]+/comments$");

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
     * 路径是否命中限流名单。
     *
     * <p>仅精确匹配发布类端点 + 视频评论发布模式，不做前缀模糊匹配——
     * /small-videos/{id}/play、/like、/dislike、/userFocus 等高频互动子路径
     * 不能被误限（曾因 startsWith 一级子路径匹配导致 App 刷视频 5 次即 429）。</p>
     */
    private boolean isLimitedPath(String path) {
        if (LIMITED_PATHS.contains(path)) {
            return true;
        }
        return VIDEO_COMMENT_PATTERN.matcher(path).matches();
    }

    /**
     * 解析客户端真实 IP。
     *
     * <p>优先级：RemoteAddress（host 网络模式下即真实客户端 IP）→ X-Real-IP → X-Forwarded-For 首段。</p>
     *
     * <p>安全考量：X-Forwarded-For / X-Real-IP 可被客户端伪造，仅当 RemoteAddress 为内网地址
     * （即请求来自反向代理/容器网络）时才信任这两个头；否则直接使用 RemoteAddress，
     * 防止伪造头绕过限流。</p>
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        String remoteAddr = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "";

        // RemoteAddress 不是内网地址 → 直连场景（host 模式），直接使用，不信任代理头
        if (!remoteAddr.isEmpty() && !isPrivateIp(remoteAddr)) {
            return remoteAddr;
        }

        // RemoteAddress 是内网地址或为空 → 请求来自反代/容器，尝试从代理头取真实 IP
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            // XFF 格式：client, proxy1, proxy2...，取第一段（真实客户端）
            return xff.split(",")[0].trim();
        }

        return remoteAddr.isEmpty() ? "unknown" : remoteAddr;
    }

    /**
     * 判断是否为内网/私有 IP（RFC 1918 + 本地回环）
     */
    private boolean isPrivateIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        // IPv4 私有地址段
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        // 本地回环
        return ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1");
    }

    @Override
    public int getOrder() {
        // 高优先级，在路由转发前拦截
        return -100;
    }
}