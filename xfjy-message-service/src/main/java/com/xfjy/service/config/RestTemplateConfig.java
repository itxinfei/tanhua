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

/**
 * WuKongIM 等下游调用的 RestTemplate 装配。
 *
 * <p>超时口径与 xfjy-server 的 DownstreamRequestFactory 保持一致：连接 2s、读取 5s。
 * RestTemplate 默认不设超时会无限等待，下游不可达时会长时间占用工作线程。</p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public ClientHttpRequestFactory simpleClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

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
