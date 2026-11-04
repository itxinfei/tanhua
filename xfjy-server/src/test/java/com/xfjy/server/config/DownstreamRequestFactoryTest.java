package com.xfjy.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 BFF 下游调用超时口径。
 *
 * <p>背景：RestClient 默认不设超时（无限等待）。Nacos 注册了不可达实例时，
 * 请求会挂到 TCP 超时（数十秒），长时间占用 BFF 工作线程，表现为前端偶发 503
 * 且响应极慢。此测试锁定口径，防止后续改动把超时改回去。</p>
 */
class DownstreamRequestFactoryTest {

    @Test
    void shouldCreateFactoryWithConnectAndReadTimeout() throws Exception {
        ClientHttpRequestFactory factory = DownstreamRequestFactory.create();

        assertNotNull(factory, "应创建出请求工厂");
        assertTrue(factory instanceof SimpleClientHttpRequestFactory,
                "应使用 SimpleClientHttpRequestFactory，避免引入额外连接池依赖");

        SimpleClientHttpRequestFactory simple = (SimpleClientHttpRequestFactory) factory;
        assertEquals(Duration.ofSeconds(2), readField(simple, "connectTimeout"),
                "连接超时须为 2s：内网可达实例建连为毫秒级，2s 可快速判定不可达");
        assertEquals(Duration.ofSeconds(5), readField(simple, "readTimeout"),
                "读取超时须为 5s：域服务存在慢查询，过短会误杀正常请求");
    }

    @Test
    void shouldCreateIndependentInstances() {
        ClientHttpRequestFactory first = DownstreamRequestFactory.create();
        ClientHttpRequestFactory second = DownstreamRequestFactory.create();

        assertTrue(first != second, "每次应返回新实例，便于将来按服务粒度差异化超时");
    }

    @Test
    void timeoutConstantsShouldBeConsistentWithDocumentedValues() {
        assertEquals(Duration.ofSeconds(2), DownstreamRequestFactory.CONNECT_TIMEOUT,
                "连接超时常量须与文档口径一致");
        assertEquals(Duration.ofSeconds(5), DownstreamRequestFactory.READ_TIMEOUT,
                "读取超时常量须与文档口径一致");
    }

    private static Duration readField(SimpleClientHttpRequestFactory target, String name) throws Exception {
        Field field = SimpleClientHttpRequestFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(target);
        return value instanceof Duration duration ? duration : Duration.ofMillis((Integer) value);
    }
}
