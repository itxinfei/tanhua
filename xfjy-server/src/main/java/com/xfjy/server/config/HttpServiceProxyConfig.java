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
import com.xfjy.service.api.WuKongImApi;
import com.xfjy.server.service.ImProviderDelegate;
import com.xfjy.server.service.SysConfigService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP Service Client 代理配置
 * 替代原 Dubbo @Reference 远程调用，服务地址解析策略：
 * 1. Spring Cloud LoadBalancer 优先：注入 @LoadBalanced RestClient.Builder，通过 lb:// 协议自动服务发现与负载均衡
 * 2. DiscoveryClient 轮询兜底：LoadBalancer 不可用时回退 DiscoveryClient + AtomicInteger 简单轮询
 *
 * - 探花匹配服务（xfjy-match-service，18082）：推荐用户/地理位置/附近的人
 * - 用户关系服务（xfjy-user-service，18090）：好友/喜欢/访客 + 用户资料
 * - 圈子动态服务（xfjy-circle-service，18084）：动态/评论/时间线
 * - 小视频服务（xfjy-video-service，18085）：视频/关注作者
 * - 消息/IM服务（xfjy-message-service，18086）：环信集成——好友注册/消息下发
 * - 支付服务（xfjy-pay-service，18089）：VIP 套餐/订单/会员状态
 */
@Configuration
public class HttpServiceProxyConfig {

    /** LoadBalancer 增强的 RestClient.Builder，通过 lb:// 协议自动服务发现与负载均衡；不可用时为 null */
    private final RestClient.Builder loadBalancedBuilder;

    /** 服务发现客户端（Nacos），LoadBalancer 不可用时的轮询兜底 */
    private final DiscoveryClient discoveryClient;

    /** 内部鉴权 Token，用于调用域服务时注入 X-Internal-Token 头 */
    private final String internalAuthToken;

    public HttpServiceProxyConfig(@LoadBalanced ObjectProvider<RestClient.Builder> loadBalancedBuilderProvider,
                                  ObjectProvider<DiscoveryClient> discoveryClientProvider,
                                  @Value("${internal.auth.tokens:}") String internalAuthTokens) {
        this.loadBalancedBuilder = loadBalancedBuilderProvider.getIfAvailable();
        this.discoveryClient = discoveryClientProvider.getIfAvailable();
        // 取第一个 token（逗号分隔时取 primary）
        this.internalAuthToken = internalAuthTokens != null && !internalAuthTokens.isBlank()
                ? internalAuthTokens.split(",")[0].trim() : "";
    }

    @Bean
    public HttpServiceProxyFactory matchServiceProxyFactory() {
        return buildFactory("xfjy-match-service");
    }

    @Bean
    public HttpServiceProxyFactory userServiceProxyFactory() {
        return buildFactory("xfjy-user-service");
    }

    @Bean
    public HttpServiceProxyFactory circleServiceProxyFactory() {
        return buildFactory("xfjy-circle-service");
    }

    @Bean
    public HttpServiceProxyFactory videoServiceProxyFactory() {
        return buildFactory("xfjy-video-service");
    }

    @Bean
    public HttpServiceProxyFactory messageServiceProxyFactory() {
        return buildFactory("xfjy-message-service");
    }

    @Bean
    public HttpServiceProxyFactory payServiceProxyFactory() {
        return buildFactory("xfjy-pay-service");
    }

    /**
     * 构建代理工厂。
     * 优先使用 @LoadBalanced RestClient.Builder 通过 lb:// 协议实现服务发现与负载均衡；
     * LoadBalancer 不可用时回退 DiscoveryClient + AtomicInteger 轮询；
     * 两者均不可用则抛出异常提示配置缺失。
     *
     * @param serviceId Nacos 服务名（须与各服务 spring.application.name 一致）
     */
    private HttpServiceProxyFactory buildFactory(String serviceId) {
        if (this.loadBalancedBuilder != null) {
            var builder = this.loadBalancedBuilder.clone()
                    .baseUrl("lb://" + serviceId);
            if (!this.internalAuthToken.isEmpty()) {
                builder.defaultHeader("X-Internal-Token", this.internalAuthToken);
            }
            RestClient restClient = builder.build();
            return HttpServiceProxyFactory
                    .builderFor(RestClientAdapter.create(restClient))
                    .build();
        }
        if (this.discoveryClient != null) {
            RestClient restClient = RestClient.builder()
                    .requestInterceptor(new ServiceInstanceInterceptor(serviceId, this.discoveryClient))
                    .build();
            return HttpServiceProxyFactory
                    .builderFor(RestClientAdapter.create(restClient))
                    .build();
        }
        throw new IllegalStateException(
                "Neither @LoadBalanced RestClient.Builder nor DiscoveryClient is available for service: " + serviceId
                + ". Please ensure spring-cloud-starter-loadbalancer is on the classpath or a DiscoveryClient bean is configured.");
    }

    /**
     * 服务发现拦截器（LoadBalancer 不可用时的轮询兜底方案）。
     * 每次请求从注册中心解析目标实例，使用 AtomicInteger 实现简单轮询并替换请求 host/port，
     * 注册中心无可用实例时回退 RestClient 默认 baseUrl。
     */
    private static final class ServiceInstanceInterceptor implements ClientHttpRequestInterceptor {

        private final String serviceId;
        private final DiscoveryClient discoveryClient;
        private final AtomicInteger counter = new AtomicInteger(0);

        ServiceInstanceInterceptor(String serviceId, DiscoveryClient discoveryClient) {
            this.serviceId = serviceId;
            this.discoveryClient = discoveryClient;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            List<ServiceInstance> instances = this.discoveryClient.getInstances(this.serviceId);
            if (instances != null && !instances.isEmpty()) {
                int index = Math.abs(this.counter.getAndIncrement()) % instances.size();
                ServiceInstance instance = instances.get(index);
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
    public HuanXinApi huanXinApiClient(@Qualifier("messageServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(HuanXinApi.class);
    }

    @Bean
    public WuKongImApi wuKongImApiClient(@Qualifier("messageServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(WuKongImApi.class);
    }

    /**
     * IM 通道委派器：按 tb_sys_config.im.provider 运行时委派
     * 5个调用点注入 HuanXinApi 类型，容器注入本委派器，实现零改动通道切换
     * 默认 wukongim（开源 WuKongIM），后台可切换回 huanxin（环信）
     */
    @Bean
    public HuanXinApi huanXinApi(HuanXinApi huanXinApiClient,
                                 WuKongImApi wuKongImApiClient,
                                 SysConfigService sysConfigService) {
        return new ImProviderDelegate(huanXinApiClient, wuKongImApiClient, sysConfigService);
    }

    // ---- 支付服务（18089）----

    @Bean
    public PayApi payApi(@Qualifier("payServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(PayApi.class);
    }
}
