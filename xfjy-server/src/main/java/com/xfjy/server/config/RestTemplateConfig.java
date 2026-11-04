package com.xfjy.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(ClientHttpRequestFactory factory) {
        RestTemplate restTemplate = new RestTemplate(factory);
        // 支持中文编码
        restTemplate.getMessageConverters().set(1, new StringHttpMessageConverter(Charset.forName("UTF-8")));
        return restTemplate;
    }

    /**
     * RestTemplate 通道的下游请求工厂，超时口径见 {@link DownstreamRequestFactory}。
     *
     * <p>连接超时原为 5s（与读取超时同值），现统一为 2s：内网可达实例建连是毫秒级，
     * 5s 连接超时在实例不可达时会白等，与 RestClient 通道口径不一致。
     */
    @Bean
    public ClientHttpRequestFactory simpleClientHttpRequestFactory() {
        return DownstreamRequestFactory.create();
    }
}
