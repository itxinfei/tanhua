package com.xfjy.server.config;

import com.xfjy.service.api.ESUserLocationApi;
import com.xfjy.service.api.PayApi;
import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.SwipeLogApi;
import com.xfjy.service.api.RecommendUserApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.api.UserLikeApi;
import com.xfjy.service.api.UserLocationApi;
import com.xfjy.service.api.UsersApi;
import com.xfjy.service.api.VideoApi;
import com.xfjy.service.api.VisitorsApi;
import com.xfjy.service.api.WuKongImApi;
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
 * 1. 静态 URL 优先：application.yml 配置了 xfjy.<name>.service.url 时直接使用（本地开发/指定地址）
 * 2. Spring Cloud LoadBalancer：未配置静态 URL 时通过 lb:// 协议服务发现与负载均衡
 * 3. DiscoveryClient 轮询兜底：LoadBalancer 不可用时回退 DiscoveryClient + AtomicInteger 简单轮询
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

    /** 静态服务地址映射（本地开发/指定地址时优先于服务发现），key=服务名，value=baseUrl */
    private final java.util.Map<String, String> staticUrls = new java.util.HashMap<>();

    public HttpServiceProxyConfig(@LoadBalanced ObjectProvider<RestClient.Builder> loadBalancedBuilderProvider,
                                  ObjectProvider<DiscoveryClient> discoveryClientProvider,
                                  @Value("${internal.auth.tokens:}") String internalAuthTokens,
                                  @Value("${xfjy.match.service.url:}") String matchUrl,
                                  @Value("${xfjy.user.service.url:}") String userUrl,
                                  @Value("${xfjy.circle.service.url:}") String circleUrl,
                                  @Value("${xfjy.video.service.url:}") String videoUrl,
                                  @Value("${xfjy.message.service.url:}") String messageUrl,
                                  @Value("${xfjy.pay.service.url:}") String payUrl,
                                  @Value("${xfjy.elasticsearch.service.url:}") String elasticsearchUrl) {
        this.loadBalancedBuilder = loadBalancedBuilderProvider.getIfAvailable();
        this.discoveryClient = discoveryClientProvider.getIfAvailable();
        // 取第一个 token（逗号分隔时取 primary）
        this.internalAuthToken = internalAuthTokens != null && !internalAuthTokens.isBlank()
                ? internalAuthTokens.split(",")[0].trim() : "";
        putIfPresent(staticUrls, "xfjy-match-service", matchUrl);
        putIfPresent(staticUrls, "xfjy-user-service", userUrl);
        putIfPresent(staticUrls, "xfjy-circle-service", circleUrl);
        putIfPresent(staticUrls, "xfjy-video-service", videoUrl);
        putIfPresent(staticUrls, "xfjy-message-service", messageUrl);
        putIfPresent(staticUrls, "xfjy-pay-service", payUrl);
        putIfPresent(staticUrls, "xfjy-elasticsearch", elasticsearchUrl);
    }

    private static void putIfPresent(java.util.Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank() && !value.startsWith("${")) {
            map.put(key, value);
        }
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

    @Bean
    public HttpServiceProxyFactory elasticsearchServiceProxyFactory() {
        return buildFactory("xfjy-elasticsearch");
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
        // 1. 静态 URL 优先（本地开发/指定地址场景）
        String staticUrl = staticUrls.get(serviceId);
        if (staticUrl != null) {
            var builder = RestClient.builder().baseUrl(staticUrl);
            if (!this.internalAuthToken.isEmpty()) {
                builder.defaultHeader("X-Internal-Token", this.internalAuthToken);
            }
            RestClient restClient = builder.build();
            return HttpServiceProxyFactory
                    .builderFor(RestClientAdapter.create(restClient))
                    .build();
        }
        // 2. LoadBalancer 服务发现
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
        // 3. DiscoveryClient 轮询兜底
        if (this.discoveryClient != null) {
            var builder = RestClient.builder()
                    .requestInterceptor(new ServiceInstanceInterceptor(serviceId, this.discoveryClient));
            if (!this.internalAuthToken.isEmpty()) {
                builder.defaultHeader("X-Internal-Token", this.internalAuthToken);
            }
            RestClient restClient = builder.build();
            return HttpServiceProxyFactory
                    .builderFor(RestClientAdapter.create(restClient))
                    .build();
        }
        throw new IllegalStateException(
                "Neither static URL nor @LoadBalanced RestClient.Builder nor DiscoveryClient is available for service: " + serviceId
                + ". Please configure xfjy.<name>.service.url or ensure spring-cloud-starter-loadbalancer is on the classpath.");
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
    public ESUserLocationApi esUserLocationApi(@Qualifier("elasticsearchServiceProxyFactory") HttpServiceProxyFactory factory) {
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

    // SwipeLogApi：滑动日志（今日滑动次数计数 + 记录），实现位于 xfjy-user-service（18090）
    @Bean
    public SwipeLogApi swipeLogApi(@Qualifier("userServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(SwipeLogApi.class);
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
    public WuKongImApi wuKongImApiClient(@Qualifier("messageServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(WuKongImApi.class);
    }

    // ---- 支付服务（18089）----

    @Bean
    public PayApi payApi(@Qualifier("payServiceProxyFactory") HttpServiceProxyFactory factory) {
        return factory.createClient(PayApi.class);
    }
}
