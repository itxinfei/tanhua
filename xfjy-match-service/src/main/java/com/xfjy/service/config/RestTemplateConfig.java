package com.xfjy.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * 下游请求工厂。RestClient 默认不设超时会无限等待，下游不可达时会长时间占用工作线程。
     *
     * <p>口径与 xfjy-server 的 DownstreamRequestFactory 保持一致：连接 2s、读取 5s。
     * 内网可达实例建连为毫秒级，过长的连接超时只会在实例不可达时白等。</p>
     */
    @Bean
    public ClientHttpRequestFactory simpleClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    /**
     * 字符串转换器用 Spring 支持的 {@code withStringConverter} 显式覆盖为 UTF-8，
     * 而非 {@code getMessageConverters().set(1, ...)} 硬编码索引——默认转换器顺序由
     * {@link HttpMessageConverters} 的 classpath 探测决定，索引 1 只是当前实现细节，
     * 属于隐式契约，框架调整顺序时会静默写错位置。
     */
    @Bean
    public RestTemplate restTemplate(ClientHttpRequestFactory factory) {
        RestTemplate restTemplate = new RestTemplate(
                HttpMessageConverters.forClient()
                        .registerDefaults()
                        .withStringConverter(new StringHttpMessageConverter(StandardCharsets.UTF_8))
                        .build());
        restTemplate.setRequestFactory(factory);
        return restTemplate;
    }
}
