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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * HTTP Service Client 代理配置
 * 替代原 Dubbo @Reference 远程调用，服务地址解析策略：
 * 1. Nacos 服务发现优先：注入 DiscoveryClient，每次请求从注册中心解析目标实例 host/port
 * 2. 静态 URL 兜底：注册中心无可用实例时回退 @Value 配置的本地地址（单服务调试场景）
 * - 探花匹配服务（xfjy-match-service，18082）：推荐用户/地理位置/附近的人
 * - 用户关系服务（xfjy-user-service，18090）：好友/喜欢/访客 + 用户资料
 * - 圈子动态服务（xfjy-circle-service，18084）：动态/评论/时间线
 * - 小视频服务（xfjy-video-service，18085）：视频/关注作者
 * - 消息/IM服务（xfjy-message-service，18086）：环信集成——好友注册/消息下发
 * - 支付服务（xfjy-pay-service，18089）：VIP 套餐/订单/会员状态
 */
@Configuration
public class HttpServiceProxyConfig {

    /** 服务发现客户端（Nacos），未接入注册中心时为 null，走静态 URL 兜底 */
    private final DiscoveryClient discoveryClient;

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

    public HttpServiceProxyConfig(ObjectProvider<DiscoveryClient> discoveryClientProvider) {
        this.discoveryClient = discoveryClientProvider.getIfAvailable();
    }

    @Bean
    public HttpServiceProxyFactory matchServiceProxyFactory() {
        return buildFactory("xfjy-match-service", matchServiceUrl);
    }

    @Bean
    public HttpServiceProxyFactory userServiceProxyFactory() {
        return buildFactory("xfjy-user-service", userServiceUrl);
    }

    @Bean
    public HttpServiceProxyFactory circleServiceProxyFactory() {
        return buildFactory("xfjy-circle-service", circleServiceUrl);
    }

    @Bean
    public HttpServiceProxyFactory videoServiceProxyFactory() {
        return buildFactory("xfjy-video-service", videoServiceUrl);
    }

    @Bean
    public HttpServiceProxyFactory messageServiceProxyFactory() {
        return buildFactory("xfjy-message-service", messageServiceUrl);
    }

    @Bean
    public HttpServiceProxyFactory payServiceProxyFactory() {
        return buildFactory("xfjy-pay-service", payServiceUrl);
    }

    /**
     * 构建代理工厂：baseUrl 为静态兜底地址，注册中心可用时由拦截器动态替换为实例地址。
     *
     * @param serviceId   Nacos 服务名（须与各服务 spring.application.name 一致）
     * @param fallbackUrl 静态兜底地址（本地调试，注册中心无实例时使用）
     */
    private HttpServiceProxyFactory buildFactory(String serviceId, String fallbackUrl) {
        RestClient.Builder builder = RestClient.builder().baseUrl(fallbackUrl);
        if (this.discoveryClient != null) {
            builder.requestInterceptor(new ServiceInstanceInterceptor(serviceId, this.discoveryClient));
        }
        RestClient restClient = builder.build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
    }

    /**
     * 服务发现拦截器：每次请求从注册中心解析目标实例并替换请求 host/port，
     * 注册中心无可用实例时回退 baseUrl 静态地址。
     * 单实例部署取第一个实例；多实例时由 DiscoveryClient 缓存列表轮询扩展。
     */
    private static final class ServiceInstanceInterceptor implements ClientHttpRequestInterceptor {

        private final String serviceId;
        private final DiscoveryClient discoveryClient;

        ServiceInstanceInterceptor(String serviceId, DiscoveryClient discoveryClient) {
            this.serviceId = serviceId;
            this.discoveryClient = discoveryClient;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            List<ServiceInstance> instances = this.discoveryClient.getInstances(this.serviceId);
            if (instances != null && !instances.isEmpty()) {
                ServiceInstance instance = instances.get(0);
                URI resolved = UriComponentsBuilder.fromUri(request.getURI())
                        .scheme(Boolean.TRUE.equals(instance.isSecure()) ? "https" : "http")
                        .host(instance.getHost())
                        .port(instance.getPort())
                        .build(true)
                        .toUri();
                HttpRequest wrapped = new HttpRequestWrapper(request) {
                    @Override
                    public URI getURI() {
                        return resolved;
                    }
                };
                return execution.execute(wrapped, body);
            }
            // 注册中心无实例：回退静态地址
            return execution.execute(request, body);
        }
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