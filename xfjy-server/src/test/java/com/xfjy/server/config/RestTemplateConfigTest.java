package com.xfjy.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link RestTemplateConfig} 的字符串转换器装配与超时口径。
 *
 * <p>背景：原实现用 {@code getMessageConverters().set(1, new StringHttpMessageConverter(UTF-8))}
 * 硬编码索引覆盖默认字符串转换器。Spring 7 的默认转换器顺序由 classpath 探测决定
 * （{@code HttpMessageConverters.forClient().registerDefaults()}），索引 1 是否为
 * {@code StringHttpMessageConverter} 取决于 ByteArray 转换器是否存在等前置条件，
 * 属于隐式契约。此测试锁定「字符串转换器为 UTF-8」这一真实意图，
 * 并对默认顺序做断言以暴露漂移。</p>
 */
class RestTemplateConfigTest {

    @Test
    void stringConverterShouldBeUtf8() {
        RestTemplate restTemplate = new RestTemplateConfig().restTemplate();

        StringHttpMessageConverter converter = findStringConverter(restTemplate.getMessageConverters());
        assertTrue(converter != null, "应存在 StringHttpMessageConverter");
        assertEquals(StandardCharsets.UTF_8, converter.getDefaultCharset(),
                "字符串转换器须为 UTF-8：下游返回中文时避免 ISO-8859-1 乱码");
    }

    @Test
    void stringConverterShouldBeReplacedNotAppended() {
        RestTemplate restTemplate = new RestTemplateConfig().restTemplate();

        long count = restTemplate.getMessageConverters().stream()
                .filter(StringHttpMessageConverter.class::isInstance)
                .count();
        assertEquals(1, count, "StringHttpMessageConverter 应恰好 1 个（替换而非追加）");
    }

    @Test
    void requestFactoryShouldFollowDownstreamTimeoutBudget() throws Exception {
        RestTemplate restTemplate = new RestTemplateConfig().restTemplate();

        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
        assertInstanceOf(SimpleClientHttpRequestFactory.class, factory,
                "请求工厂须为 SimpleClientHttpRequestFactory");

        SimpleClientHttpRequestFactory simple = (SimpleClientHttpRequestFactory) factory;
        assertEquals(Duration.ofSeconds(2), readTimeoutField(simple, "connectTimeout"),
                "连接超时须与 DownstreamRequestFactory 口径一致（2s）");
        assertEquals(Duration.ofSeconds(5), readTimeoutField(simple, "readTimeout"),
                "读取超时须与 DownstreamRequestFactory 口径一致（5s）");
    }

    private static Duration readTimeoutField(SimpleClientHttpRequestFactory target, String name) throws Exception {
        Field field = SimpleClientHttpRequestFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(target);
        return value instanceof Duration duration ? duration : Duration.ofMillis((Integer) value);
    }

    @Test
    void defaultConverterOrderShouldHaveStringAtExpectedIndex() {
        // 锁定 Spring 7 默认顺序：若框架调整顺序，此断言会失败，
        // 提醒维护者同步检查 RestTemplateConfig 的覆盖逻辑。
        List<HttpMessageConverter<?>> defaults = new RestTemplate().getMessageConverters();
        List<String> classes = new ArrayList<>();
        defaults.forEach(c -> classes.add(c.getClass().getSimpleName()));

        int stringIndex = -1;
        for (int i = 0; i < defaults.size(); i++) {
            if (defaults.get(i) instanceof StringHttpMessageConverter) {
                stringIndex = i;
                break;
            }
        }
        assertTrue(stringIndex >= 0, "默认列表应含 StringHttpMessageConverter，实际: " + classes);
        assertEquals(1, stringIndex,
                "Spring 7 默认顺序中 StringHttpMessageConverter 应位于索引 1，实际顺序: " + classes);
        assertInstanceOf(StringHttpMessageConverter.class, defaults.get(stringIndex));
    }

    private static StringHttpMessageConverter findStringConverter(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof StringHttpMessageConverter stringConverter) {
                return stringConverter;
            }
        }
        return null;
    }
}
