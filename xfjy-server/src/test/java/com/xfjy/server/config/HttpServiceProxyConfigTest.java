package com.xfjy.server.config;

import com.xfjy.service.api.PayApi;
import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.RecommendUserApi;
import com.xfjy.service.api.SwipeLogApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.api.UserLikeApi;
import com.xfjy.service.api.UserLocationApi;
import com.xfjy.service.api.UsersApi;
import com.xfjy.service.api.VideoApi;
import com.xfjy.service.api.VisitorsApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证 {@link HttpServiceProxyConfig} 在真实容器装配下能为全部域服务创建代理 Bean。
 *
 * <p>该配置类有三条解析路径（静态 URL / LoadBalancer / DiscoveryClient 兜底），
 * 任一分支抛异常都会导致 Bean 创建失败、应用启动不了，因此此处按 Bean 存在性锁定。
 * 超时挂载口径由 {@link DownstreamRequestFactoryTest} 单独覆盖。</p>
 */
@SpringBootTest(classes = {HttpServiceProxyConfig.class},
        properties = {
                "internal.auth.tokens=test-internal-token",
                "xfjy.match.service.url=http://127.0.0.1:18082",
                "xfjy.user.service.url=http://127.0.0.1:18090",
                "xfjy.circle.service.url=http://127.0.0.1:18084",
                "xfjy.video.service.url=http://127.0.0.1:18085",
                "xfjy.message.service.url=http://127.0.0.1:18086",
                "xfjy.pay.service.url=http://127.0.0.1:18089",
                "xfjy.elasticsearch.service.url=http://127.0.0.1:18088",
        })
class HttpServiceProxyConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldCreateAllDomainServiceClients() {
        // 静态 URL 路径：本测试通过 properties 注入，走 buildFactory 的分支 1
        assertBeanExists(RecommendUserApi.class, "match-service");
        assertBeanExists(UserLocationApi.class, "match-service");
        assertBeanExists(UsersApi.class, "user-service");
        assertBeanExists(UserLikeApi.class, "user-service");
        assertBeanExists(VisitorsApi.class, "user-service");
        assertBeanExists(UserInfoApi.class, "user-service");
        assertBeanExists(SwipeLogApi.class, "user-service");
        assertBeanExists(QuanZiApi.class, "circle-service");
        assertBeanExists(VideoApi.class, "video-service");
        assertBeanExists(PayApi.class, "pay-service");
    }

    private void assertBeanExists(Class<?> type, String service) {
        assertNotNull(context.getBean(type), service + " 的客户端 Bean 应被创建: " + type.getSimpleName());
    }
}
