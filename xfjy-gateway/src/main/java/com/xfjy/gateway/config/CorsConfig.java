package com.xfjy.gateway.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 网关全局跨域配置（CORS 唯一收敛点，见 AGENTS.md §13.7）。
 * 前端（H5 5173 / admin 5174 / 生产域名）与网关(8090)不同源，浏览器预检需 CORS 放行。
 * origins 必须为受信任域名白名单（禁止 * 通配）；前端使用 Authorization: Bearer 而非 Cookie，
 * 故 allowCredentials=false，从根上消除浏览器自动携带凭证导致的 CSRF 风险。
 */
@Configuration
public class CorsConfig {

    /** 受信任前端域名白名单（逗号分隔）；生产通过部署注入，禁止 * 通配 */
    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private List<String> allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 白名单精确匹配，禁止 * 通配（AGENTS.md §13.7）
        config.setAllowedOriginPatterns(allowedOrigins);
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        // 前端使用 Bearer Token，不依赖 Cookie，关闭凭证携带以杜绝 CSRF 自动携带
        config.setAllowCredentials(false);
        // 预检结果缓存 1 小时，减少 OPTIONS 请求
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}