package com.xfjy.service.config;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * match 域 Mongo 集合索引启动期幂等创建（recommend_user / user_location）。
 *
 * <p>背景：RecommendUser、UserLocation 两个实体虽声明了 {@code @Indexed} /
 * {@code @CompoundIndex}，但项目未开启 Mongo 自动建索引（见 video 模块
 * VideoGeoIndexInitializer 同类说明），jar 内实体靠 {@code mongoTemplate.xxx(query, X.class)}
 * 动态传入，注解不会落成物理索引，线上实测两个集合均仅有 _id_。本初始化器在启动期显式兜底，
 * 防止容器重建/换库后索引丢失。</p>
 *
 * <p>索引定义严格对齐实体注解：单字段索引名沿用 Spring Data Mongo 默认的 {@code 字段_方向}；
 * user_location 的 2dsphere 沿用注解显式命名 location_index。createIndexes 幂等，
 * 同名定义冲突只记 ERROR 不阻断启动，由运维按日志处理。</p>
 */
@Component
public class MatchMongoIndexInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchMongoIndexInitializer.class);

    private static final String COLLECTION_RECOMMEND_USER = "recommend_user";
    private static final String COLLECTION_USER_LOCATION = "user_location";

    private final MongoTemplate mongoTemplate;

    public MatchMongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // RecommendUser 注解：@Indexed userId、@Indexed score
        Document recommendByUser = new Document("key", new Document("userId", 1))
                .append("name", "userId_1");
        Document recommendByScore = new Document("key", new Document("score", 1))
                .append("name", "score_1");
        ensureIndexes(COLLECTION_RECOMMEND_USER, List.of(recommendByUser, recommendByScore));

        // UserLocation 注解：@Indexed userId + @CompoundIndex(location 2dsphere, name=location_index)
        Document locationByUser = new Document("key", new Document("userId", 1))
                .append("name", "userId_1");
        Document locationGeo = new Document("key", new Document("location", "2dsphere"))
                .append("name", "location_index");
        ensureIndexes(COLLECTION_USER_LOCATION, List.of(locationByUser, locationGeo));
    }

    private void ensureIndexes(String collection, List<Document> indexes) {
        try {
            Document command = new Document("createIndexes", collection)
                    .append("indexes", indexes);
            Document result = this.mongoTemplate.getDb().runCommand(command);
            LOGGER.info("Mongo 集合 {} 索引已就绪（幂等创建），result={}", collection, result.get("ok"));
        } catch (Exception e) {
            // 不阻断启动：索引失败只影响相关查询性能，不影响服务可用性，留 ERROR 便于运维介入
            LOGGER.error("创建集合 {} 的索引失败，请检查是否存在同名冲突索引", collection, e);
        }
    }
}
