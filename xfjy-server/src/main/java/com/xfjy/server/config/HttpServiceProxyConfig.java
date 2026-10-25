package com.xfjy.server.config;

import com.xfjy.service.api.ESUserLocationApi;
import com.xfjy.service.api.HuanXinApi;
import com.xfjy.service.api.PayApi;
import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.RecommendUserApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.api.UserLikeApi;
import com.xfjy.service.api.UserLocationApi;
import com.xfjy.service.api.UsersApi;
import com.xfjy.service.api.VideoApi;
import com.xfjy.service.api.VisitorsApi;
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
 * - 探花匹配服务（xfjy-match-service，18082）：推荐用户/地理位置/附近的人（阶段三迁出，data-core退役）
 * - 用户关系服务（xfjy-user-service，18090）：好友/喜欢/访客等关系域数据（微服务化阶段二迁出）+ 用户资料（阶段三迁出）
 * - 消息/IM服务（xfjy-message-service，18086）：环信集成——好友注册/消息下发（阶段三迁出，原sso HuanXinController契约化）
 */
@Configuration
public class HttpServiceProxyConfig {

    @Value("${xfjy.match.service.url:http://127.0.0.1:18082}")
    private String matchServiceUrl;

    @Value("${xfjy.user.service.url:http://127.0.0.1:18090}")
    private String userServiceUrl;

    @Value("${xfjy.circle.service.url:http://127.0.0.1:18084}")
    private String circleServiceUrl;

    @Value("${xfjy.video.service.url:http://127.0.0.1:18085}")
    private String videoServiceUrl;

    @Value("${xfjy.message.service.url:http://127.0.0.1:18086}")
    private String messageServiceUrl;

    @Value("${xfjy.pay.service.url:http://127.0.0.1:18089}")
    private String payServiceUrl;

    @Bean
    public HttpServiceProxyFactory matchServiceProxyFactory() {
        return buildFactory(matchServiceUrl);
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

    @Bean
    public HttpServiceProxyFactory messageServiceProxyFactory() {
        return buildFactory(messageServiceUrl);
    }

    @Bean
    public HttpServiceProxyFactory payServiceProxyFactory() {
        return buildFactory(payServiceUrl);
    }

    private HttpServiceProxyFactory buildFactory(String baseUrl) {
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
    }

    // ---- 探花匹配服务（18082）----

    @Bean
    public RecommendUserApi recommendUserApi(@Qualifier("matchServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(RecommendUserApi.class);
    }

    @Bean
    public UserLocationApi userLocationApi(@Qualifier("matchServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(UserLocationApi.class);
    }

    @Bean
    public ESUserLocationApi esUserLocationApi(@Qualifier("matchServiceProxyFactory") HttpServiceProxyFactory factory) {
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

    @Bean
    public UserInfoApi userInfoApi(@Qualifier("userServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(UserInfoApi.class);
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

    // ---- 消息/IM服务（18086）----

    @Bean
    public HuanXinApi huanXinApi(@Qualifier("messageServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(HuanXinApi.class);
    }

    // ---- 支付服务（18089）----

    @Bean
    public PayApi payApi(@Qualifier("payServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(PayApi.class);
    }
}