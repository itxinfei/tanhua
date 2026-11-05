package com.xfjy.server.config;

import com.xfjy.service.api.VideoSearchApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * 视频搜索 HTTP 客户端注册
 *
 * <p>复用 {@code HttpServiceProxyConfig} 已构建的 xfjy-video-service 代理工厂
 * （含 X-Internal-Token 注入、超时、Nacos 服务发现），只补一个 Bean，
 * 不改动共享的 HttpServiceProxyConfig 文件。</p>
 */
@Configuration
public class VideoSearchClientConfig {

    @Bean
    public VideoSearchApi videoSearchApi(@Qualifier("videoServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(VideoSearchApi.class);
    }
}
