package com.xfjy.api.json;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 xfjy-api 共享的 ObjectId Jackson 3 规则：hex 序列化、反序列化往返，以及自动装配注册。
 *
 * <p>用 ApplicationContextRunner 装配 Boot 的 JacksonAutoConfiguration + 本模块自动配置，
 * 得到的 JsonMapper 与各服务生产容器中的是同一 Bean 形态。</p>
 */
class XfjyJacksonAutoConfigurationTest {

    private static final String HEX_ID = "507f1f77bcf86cd799439011";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    XfjyJacksonAutoConfiguration.class));

    @Test
    void objectIdShouldBeSerializedAsHexString() {
        runner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            IdHolder holder = new IdHolder();
            holder.setId(new ObjectId(HEX_ID));

            String json = mapper.writeValueAsString(holder);

            assertEquals("{\"id\":\"" + HEX_ID + "\"}", json,
                    "ObjectId 应输出为 24 位 hex，实际: " + json);
        });
    }

    @Test
    void objectIdShouldRoundTripFromHexString() {
        runner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            IdHolder restored = mapper.readValue("{\"id\":\"" + HEX_ID + "\"}", IdHolder.class);

            assertNotNull(restored.getId(), "hex 反序列化后 ObjectId 不应为 null");
            assertEquals(HEX_ID, restored.getId().toHexString(), "hex 往返应还原同一 ObjectId");
        });
    }

    @Test
    void autoConfigurationShouldBeRegisteredInImportsFile() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class,
                getClass().getClassLoader());

        assertTrue(candidates.getCandidates().contains(XfjyJacksonAutoConfiguration.class.getName()),
                "自动装配需通过 META-INF/spring/...AutoConfiguration.imports 注册，实际: "
                        + candidates.getCandidates());
    }

    static class IdHolder {
        private ObjectId id;

        public ObjectId getId() {
            return id;
        }

        public void setId(ObjectId id) {
            this.id = id;
        }
    }
}
