package com.xfjy.api.json;

import org.bson.types.ObjectId;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * MongoDB ObjectId 的 Jackson 3 模块：序列化为 24 位十六进制字符串，反序列化时还原为 ObjectId。
 * 由 xfjy-api 统一提供，供所有依赖它的服务共享，避免各服务重复实现导致格式不一致。
 */
public class MongoObjectIdModule extends SimpleModule {

    public static final String MODULE_NAME = "MongoObjectIdModule";

    public MongoObjectIdModule() {
        super(MODULE_NAME);
        addSerializer(ObjectId.class, new ValueSerializer<ObjectId>() {
            @Override
            public void serialize(ObjectId value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(value.toHexString());
            }
        });
        addDeserializer(ObjectId.class, new ValueDeserializer<ObjectId>() {
            @Override
            public ObjectId deserialize(JsonParser p, DeserializationContext ctxt) {
                return new ObjectId(p.getString());
            }
        });
    }
}
