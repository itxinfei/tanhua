package com.xfjy.user.config;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * user_like 集合索引启动期幂等创建。
 *
 * <p>背景：UserLike 类在 userId / likeUserId 上声明了 {@code @Indexed}，但项目未开启 Mongo
 * 自动建索引（见 video 模块 VideoGeoIndexInitializer 同类说明），jar 内实体靠
 * {@code mongoTemplate.find/count(query, UserLike.class)} 动态传入，注解不会落成物理索引，
 * 线上实测该集合仅有 _id_。本初始化器在启动期显式兜底。</p>
 *
 * <p>两个单字段索引覆盖 UserLikeApiImpl 的全部查询：userId 等值（我的喜欢/喜欢数）、
 * likeUserId 等值（粉丝数/粉丝列表）、userId+likeUserId 组合（MongoDB 可由 userId_1 前缀命中）。
 * 索引名沿用 Spring Data Mongo 对单字段 {@code @Indexed} 的默认命名 {@code 字段_方向}，
 * 与将来若开启自动建索引生成的名字保持一致，避免重复索引。createIndexes 幂等，冲突只记
 * ERROR 不阻断启动。</p>
 */
@Component
public class UserRelationMongoIndexInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserRelationMongoIndexInitializer.class);

    private static final String COLLECTION_USER_LIKE = "user_like";

    private final MongoTemplate mongoTemplate;

    public UserRelationMongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Document byUser = new Document("key", new Document("userId", 1))
                .append("name", "userId_1");
        Document byLikeUser = new Document("key", new Document("likeUserId", 1))
                .append("name", "likeUserId_1");
        ensureIndexes(COLLECTION_USER_LIKE, List.of(byUser, byLikeUser));
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
