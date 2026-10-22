package com.xfjy.server.config;

import com.xfjy.dubbo.server.api.ESUserLocationApi;
import com.xfjy.dubbo.server.api.QuanZiApi;
import com.xfjy.dubbo.server.api.RecommendUserApi;
import com.xfjy.dubbo.server.api.UserLikeApi;
import com.xfjy.dubbo.server.api.UserLocationApi;
import com.xfjy.dubbo.server.api.UsersApi;
import com.xfjy.dubbo.server.api.VideoApi;
import com.xfjy.dubbo.server.api.VisitorsApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * HTTP Service Client 代理配置
 * 替代原 Dubbo @Reference 远程调用：
 * - 数据服务（xfjy-service-core，18083）：圈子/视频/推荐/位置等 Mongo 数据
 * - 用户关系服务（xfjy-user-service，18090）：好友/喜欢/访客等关系域数据（微服务化阶段二迁出）
 */
@Configuration
public class HttpServiceProxyConfig {

    @Value("${xfjy.dubbo.service.url:http://127.0.0.1:18083}")
    private String dubboServiceUrl;

    @Value("${xfjy.user.service.url:http://127.0.0.1:18090}")
    private String userServiceUrl;

    @Value("${xfjy.circle.service.url:http://127.0.0.1:18084}")
    private String circleServiceUrl;

    @Value("${xfjy.video.service.url:http://127.0.0.1:18085}")
    private String videoServiceUrl;

    @Bean
    public HttpServiceProxyFactory httpServiceProxyFactory() {
        return buildFactory(dubboServiceUrl);
    }

    @Bean
    public HttpServiceProxyFactory userServiceProxyFactory() {
        return buildFactory(userServiceUrl);
    }

    @Bean
    public HttpServiceProxyFactory circleServiceProxyFactory() {
        return buildFactory(circleServiceUrl);
    }

    @Bean
    public HttpServiceProxyFactory videoServiceProxyFactory() {
        return buildFactory(videoServiceUrl);
    }

    private HttpServiceProxyFactory buildFactory(String baseUrl) {
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
    }

    // ---- 数据服务（18083）----

    @Bean
    public RecommendUserApi recommendUserApi(HttpServiceProxyFactory factory) {
        return factory.createClient(RecommendUserApi.class);
    }

    @Bean
    public UserLocationApi userLocationApi(HttpServiceProxyFactory factory) {
        return factory.createClient(UserLocationApi.class);
    }

    @Bean
    public ESUserLocationApi esUserLocationApi(HttpServiceProxyFactory factory) {
        return factory.createClient(ESUserLocationApi.class);
    }

    // ---- 用户关系服务（18090）----

    @Bean
    public UsersApi usersApi(@Qualifier("userServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(UsersApi.class);
    }

    @Bean
    public UserLikeApi userLikeApi(@Qualifier("userServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(UserLikeApi.class);
    }

    @Bean
    public VisitorsApi visitorsApi(@Qualifier("userServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(VisitorsApi.class);
    }

    // ---- 圈子动态服务（18084）----

    @Bean
    public QuanZiApi quanZiApi(@Qualifier("circleServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(QuanZiApi.class);
    }

    // ---- 小视频服务（18085）----

    @Bean
    public VideoApi videoApi(@Qualifier("videoServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(VideoApi.class);
    }
}