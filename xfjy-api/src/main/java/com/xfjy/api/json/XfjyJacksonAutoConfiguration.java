package com.xfjy.api.json;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/**
 * 把 {@link MongoObjectIdModule} 注册到 Spring Boot 管理的 Jackson 3 {@link JsonMapper}，
 * 使 MVC 入站/出站、Boot 配置的 RestClient/RestTemplate 与服务间 @HttpExchange 调用统一输出 hex。
 *
 * <p>仅当使用方同时具备 Spring Boot 自动配置与 Jackson 3 时生效；
 * Jackson 2（如 MinIO SDK 自带）不在此处注册，也不影响 Web 层。</p>
 */
@AutoConfiguration
@ConditionalOnClass({JsonMapper.class, JsonMapperBuilderCustomizer.class})
public class XfjyJacksonAutoConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer mongoObjectIdCustomizer() {
        return builder -> builder.addModule(new MongoObjectIdModule());
    }
}
