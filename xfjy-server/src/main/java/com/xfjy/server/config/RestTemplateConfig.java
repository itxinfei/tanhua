package com.xfjy.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Configuration
public class RestTemplateConfig {

    /**
     * RestTemplate 通道的下游请求工厂，超时口径见 {@link DownstreamRequestFactory}。
     *
     * <p>字符串转换器用 Spring 支持的 {@code withStringConverter} 显式覆盖为 UTF-8，
     * 而非 {@code getMessageConverters().set(1, ...)} 硬编码索引——默认转换器顺序由
     * {@link HttpMessageConverters} 的 classpath 探测决定，索引 1 只是当前实现细节，
     * 属于隐式契约，框架调整顺序时会静默写错位置。</p>
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate(
                HttpMessageConverters.forClient()
                        .registerDefaults()
                        .withStringConverter(new StringHttpMessageConverter(StandardCharsets.UTF_8))
                        .build());
        restTemplate.setRequestFactory(DownstreamRequestFactory.create());
        return restTemplate;
    }
}
