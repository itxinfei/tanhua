package com.xfjy.server.config;

import com.xfjy.api.json.XfjyJacksonAutoConfiguration;
import com.xfjy.service.pojo.Publish;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.Date;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 验证 xfjy-api 提供的 ObjectId 自动装配真正作用在容器（Boot 自动配置）使用的 JsonMapper 上。
 *
 * <p>用 ApplicationContextRunner 只装配 JacksonAutoConfiguration + XfjyJacksonAutoConfiguration，
 * 拿到的 JsonMapper 与生产环境 MVC / RestClient 使用的是同一个 Bean，因此断言等价于接口行为。
 * 服务端额外的 RestClient 装配（HttpServiceProxyConfig）单独覆盖。</p>
 */
class JacksonConfigTest {

    private static final String HEX_ID = "507f1f77bcf86cd799439011";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    XfjyJacksonAutoConfiguration.class));

    @Test
    void objectIdShouldBeSerializedAsHexString() {
        runner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            Publish publish = new Publish();
            publish.setId(new ObjectId(HEX_ID));
            publish.setUserId(1001L);
            publish.setCreated(1735689600000L);

            String json = mapper.writeValueAsString(publish);

            assertTrue(json.contains("\"id\":\"" + HEX_ID + "\""),
                    "ObjectId 应输出为十六进制字符串，实际: " + json);
            assertTrue(json.contains("\"created\":1735689600000"),
                    "Long 时间戳应保持数值，实际: " + json);
            assertTrue(!json.contains("\"timestamp\""),
                    "不应出现 ObjectId 内部字段，实际: " + json);
        });
    }

    @Test
    void objectIdShouldRoundTripFromHexString() {
        runner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            Publish publish = new Publish();
            publish.setId(new ObjectId(HEX_ID));
            publish.setUserId(1001L);

            String json = mapper.writeValueAsString(publish);
            Publish restored = mapper.readValue(json, Publish.class);

            assertNotNull(restored.getId(), "反序列化后 ObjectId 不应为 null");
            assertEquals(HEX_ID, restored.getId().toHexString(), "hex 往返应还原同一 ObjectId");
            assertEquals(1001L, restored.getUserId());
        });
    }

    @Test
    void dateShouldUseBootDefaultIsoFormat() {
        runner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            DateHolder holder = new DateHolder();
            holder.setCreateTime(new Date(1735689600000L));

            String json = mapper.writeValueAsString(holder);

            // 原 JacksonConfig 未设置日期格式，这里锁定 Boot 4 默认的 ISO-8601 输出，防止后续漂移
            assertTrue(json.matches(".*\"createTime\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[^\"]*\".*"),
                    "Date 应按 ISO-8601 字符串输出，实际: " + json);
        });
    }

    @Test
    void restClientShouldSerializeObjectIdAsHexWithContainerMapper() {
        runner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);
            // 与 HttpServiceProxyConfig 相同的装配方式：静态 builder + 容器 JsonMapper
            RestClient.Builder builder = HttpServiceProxyConfig.applyContainerJsonMapper(RestClient.builder(), mapper);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            Publish publish = new Publish();
            publish.setId(new ObjectId(HEX_ID));
            publish.setUserId(1001L);

            server.expect(requestTo("http://localhost:18084/publish"))
                    .andExpect(content().string(containsString("\"id\":\"" + HEX_ID + "\"")))
                    .andRespond(withSuccess());

            RestClient client = builder.baseUrl("http://localhost:18084").build();
            client.post().uri("/publish").contentType(MediaType.APPLICATION_JSON).body(publish)
                    .retrieve().toBodilessEntity();

            server.verify();
        });
    }

    static class DateHolder {
        private Date createTime;

        public Date getCreateTime() {
            return createTime;
        }

        public void setCreateTime(Date createTime) {
            this.createTime = createTime;
        }
    }
}
