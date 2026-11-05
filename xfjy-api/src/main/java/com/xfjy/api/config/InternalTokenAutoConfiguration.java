package com.xfjy.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 内部服务鉴权自动装配：注册 {@link XInternalTokenInterceptor} 并挂到 {@code /**}。
 *
 * <p>替代原 8 个领域服务各自维护的 {@code XInternalTokenInterceptor} + {@code WebMvcConfig}
 * 副本（逐字节等价）。各服务只需依赖 {@code xfjy-api}，无需任何配置类。</p>
 *
 * <p>拦截路径默认 {@code /**}，排除 {@code /actuator/**} 与 {@code /error}；
 * 可用 {@code xfjy.internal.exclude-paths} 追加排除项（逗号分隔）。</p>
 *
 * <p>启用开关 {@code xfjy.internal.enabled}：缺省（未配置）为 {@code true}，8 个领域服务
 * 零配置即生效；BFF（xfjy-server）位于网关之后、入站不校验内部 token，显式配 {@code false}
 * 关闭本装配，避免绕过网关直连 BFF 被误拦（见需求文档 §11.7-37）。仅控制入站拦截，
 * 不影响 BFF→领域服务的出站头注入（{@code HttpServiceProxyConfig} 独立装配）。</p>
 */
@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnProperty(prefix = "xfjy.internal", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InternalTokenAutoConfiguration {

    @Bean
    public XInternalTokenInterceptor xInternalTokenInterceptor(
            @Value("${internal.auth.tokens:}") String tokens) {
        return new XInternalTokenInterceptor(tokens);
    }

    @Bean
    public WebMvcConfigurer internalTokenWebMvcConfigurer(
            XInternalTokenInterceptor interceptor,
            @Value("${xfjy.internal.exclude-paths:/actuator/**,/error}") String excludePaths) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/**")
                        .excludePathPatterns(excludePaths.split(","));
            }
        };
    }
}
