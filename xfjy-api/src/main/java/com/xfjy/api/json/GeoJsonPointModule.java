package com.xfjy.api.json;

import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * {@link GeoJsonPoint} 的 Jackson 3 反序列化模块。
 *
 * <p>Spring Data MongoDB 的 {@code GeoJsonPoint} 在 Jackson 3 下会被判定为 simple type，
 * 服务间 @HttpExchange 调用把它当成响应 DTO 字段（如 {@code Video.geo}）时无法反序列化，
 * 抛出 {@code HttpMessageConversionException: Type definition error}。此处按
 * {@code {x,y}}（缺省回退 {@code coordinates[0,1]}）还原为 {@link GeoJsonPoint}。
 * 序列化沿用默认 bean 输出，无需自定义。</p>
 */
public class GeoJsonPointModule extends SimpleModule {

    public static final String MODULE_NAME = "GeoJsonPointModule";

    public GeoJsonPointModule() {
        super(MODULE_NAME);
        addDeserializer(GeoJsonPoint.class, new ValueDeserializer<GeoJsonPoint>() {
            @Override
            public GeoJsonPoint deserialize(JsonParser p, DeserializationContext ctxt)
                    throws JacksonException {
                JsonNode node = ctxt.readTree(p);
                JsonNode x = node.get("x");
                JsonNode y = node.get("y");
                if (x != null && y != null && x.isNumber() && y.isNumber()) {
                    return new GeoJsonPoint(x.asDouble(), y.asDouble());
                }
                JsonNode coordinates = node.get("coordinates");
                if (coordinates != null && coordinates.isArray() && coordinates.size() >= 2
                        && coordinates.get(0).isNumber() && coordinates.get(1).isNumber()) {
                    return new GeoJsonPoint(coordinates.get(0).asDouble(), coordinates.get(1).asDouble());
                }
                return (GeoJsonPoint) ctxt.handleUnexpectedToken(GeoJsonPoint.class, p);
            }
        });
    }
}
