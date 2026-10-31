package com.xfjy.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关内部鉴权 Token 透传过滤器。
 * 给所有经网关转发的请求自动注入 X-Internal-Token 头，
 * 确保下游服务（xfjy-sso / xfjy-server）在调用域服务时能透传内部鉴权凭证。
 */
@Component
public class InternalTokenFilter implements GlobalFilter, Ordered {

    private final String internalAuthToken;

    public InternalTokenFilter(@Value("${internal.auth.tokens:}") String internalAuthTokens) {
        this.internalAuthToken = internalAuthTokens != null && !internalAuthTokens.isBlank()
                ? internalAuthTokens.split(",")[0].trim() : "";
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (this.internalAuthToken.isEmpty()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header("X-Internal-Token", this.internalAuthToken)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        // 在限流之后（-100）、路由之前执行
        return 0;
    }
}
