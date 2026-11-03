package com.xfjy.circle;

import com.xfjy.api.json.XfjyJacksonAutoConfiguration;
import com.xfjy.service.pojo.Publish;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 提供方服务（circle）的 ObjectId JSON 契约验证：
 * 入站 hex → ObjectId，出站 ObjectId → hex。
 *
 * <p>使用 ApplicationContextRunner 装配 Boot JacksonAutoConfiguration + xfjy-api 自动配置，
 * 得到的 JsonMapper 与服务生产容器中的是同一 Bean 形态（同一自动配置 + 同一模块）；
 * 无需启动 Mongo/Redis/Nacos 即可验证 JSON 契约。</p>
 */
class ObjectIdJsonTest {

    private static final String HEX_ID = "507f1f77bcf86cd799439011";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    XfjyJacksonAutoConfiguration.class));

    @Test
    void inboundHexShouldDeserializeToObjectId() {
        runner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            String json = "{\"id\":\"" + HEX_ID + "\",\"userId\":1001,\"created\":1735689600000}";
            Publish publish = mapper.readValue(json, Publish.class);

            assertNotNull(publish.getId(), "入参 hex 应能反序列化为 ObjectId");
            assertEquals(HEX_ID, publish.getId().toHexString());
            assertEquals(1001L, publish.getUserId());
        });
    }

    @Test
    void outboundRestClientShouldWriteObjectIdAsHex() {
        runner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            Publish publish = new Publish();
            publish.setId(new ObjectId(HEX_ID));
            publish.setUserId(1001L);

            RestClient.Builder builder = RestClient.builder().configureMessageConverters(
                    converters -> converters.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper)));
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://localhost:18084/publish"))
                    .andExpect(content().string(containsString("\"id\":\"" + HEX_ID + "\"")))
                    .andRespond(withSuccess());

            RestClient client = builder.baseUrl("http://localhost:18084").build();
            client.post().uri("/publish").contentType(MediaType.APPLICATION_JSON).body(publish)
                    .retrieve().toBodilessEntity();

            server.verify();
        });
    }
}
