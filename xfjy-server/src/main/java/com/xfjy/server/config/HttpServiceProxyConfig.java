package com.xfjy.server.config;

import com.xfjy.dubbo.server.api.ESUserLocationApi;
import com.xfjy.dubbo.server.api.QuanZiApi;
import com.xfjy.dubbo.server.api.RecommendUserApi;
import com.xfjy.dubbo.server.api.UserLikeApi;
import com.xfjy.dubbo.server.api.UserLocationApi;
import com.xfjy.dubbo.server.api.UsersApi;
import com.xfjy.dubbo.server.api.VideoApi;
import com.xfjy.dubbo.server.api.VisitorsApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * HTTP Service Client 代理配置
 * 替代原 Dubbo @Reference 远程调用，通过 RestClient 调用 dubbo-service 的 REST 接口
 */
@Configuration
public class HttpServiceProxyConfig {

    @Value("${xfjy.dubbo.service.url:http://127.0.0.1:18083}")
    private String dubboServiceUrl;

    @Bean
    public HttpServiceProxyFactory httpServiceProxyFactory() {
        RestClient restClient = RestClient.builder()
                .baseUrl(dubboServiceUrl)
                .build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
    }

    @Bean
    public UsersApi usersApi(HttpServiceProxyFactory factory) {
        return factory.createClient(UsersApi.class);
    }

    @Bean
    public QuanZiApi quanZiApi(HttpServiceProxyFactory factory) {
        return factory.createClient(QuanZiApi.class);
    }

    @Bean
    public RecommendUserApi recommendUserApi(HttpServiceProxyFactory factory) {
        return factory.createClient(RecommendUserApi.class);
    }

    @Bean
    public VideoApi videoApi(HttpServiceProxyFactory factory) {
        return factory.createClient(VideoApi.class);
    }

    @Bean
    public UserLikeApi userLikeApi(HttpServiceProxyFactory factory) {
        return factory.createClient(UserLikeApi.class);
    }

    @Bean
    public UserLocationApi userLocationApi(HttpServiceProxyFactory factory) {
        return factory.createClient(UserLocationApi.class);
    }

    @Bean
    public ESUserLocationApi esUserLocationApi(HttpServiceProxyFactory factory) {
        return factory.createClient(ESUserLocationApi.class);
    }

    @Bean
    public VisitorsApi visitorsApi(HttpServiceProxyFactory factory) {
        return factory.createClient(VisitorsApi.class);
    }
}