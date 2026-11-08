package com.xfjy.circle.config;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * quanzi_comment 复合索引启动期幂等创建。
 *
 * <p>背景：Comment 类虽声明了 {@code @CompoundIndexes}，但项目未开启 Mongo 自动建索引
 * （见 VideoGeoIndexInitializer 同类说明），jar 内实体靠 {@code mongoTemplate.find(query, Comment.class)}
 * 动态传入，注解不会落成物理索引。2026-11-05 线上 200 万文档全表扫（COLLSCAN 2.7s）撞穿
 * BFF 5s 读超时导致 /messages/* 503，即源于此。这里在启动期显式 createIndexes 兜底，
 * 防止容器重建/换库后索引再次丢失。</p>
 *
 * <p>createIndexes 幂等：索引已存在且定义一致时直接返回成功；同名但定义冲突只记 ERROR
 * 不阻断启动，由运维按日志处理。索引名/字段与 Comment 的 {@code @CompoundIndex} 严格一致。</p>
 */
@Component
public class CircleMongoIndexInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CircleMongoIndexInitializer.class);

    private static final String COLLECTION_COMMENT = "quanzi_comment";

    private final MongoTemplate mongoTemplate;

    public CircleMongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // queryCommentListByUser：publishUserId + commentType + deleted 等值 + created DESC 排序
        // 加 deleted 进索引：count/find 查询条件含 deleted != true，索引覆盖后无需回表，
        // 解决 200 万文档下 count 回表扫全部记录撞穿 BFF 5s 读超时导致 /messages/* 503（2026-11-05 复发根因）
        Document byUser = new Document("key",
                        new Document("publishUserId", 1).append("commentType", 1)
                                .append("deleted", 1).append("created", -1))
                .append("name", "idx_pub_user_type_deleted_created");
        // queryCommentList / saveLikeComment / saveLoveComment：publishId + commentType + deleted 等值 + created ASC
        Document byPublish = new Document("key",
                        new Document("publishId", 1).append("commentType", 1)
                                .append("deleted", 1).append("created", 1))
                .append("name", "idx_pub_id_type_deleted_created");
        // 楼中楼：queryCommentReplyList / queryCommentReplySummary 按 parentId 等值 + commentType/deleted 过滤 + created 排序
        Document byParent = new Document("key",
                        new Document("parentId", 1).append("commentType", 1)
                                .append("deleted", 1).append("created", 1))
                .append("name", "idx_parent_id_type_deleted_created");
        // 旧索引（不含 deleted）保留：同名冲突会导致 createIndexes 失败，旧索引可减少回表范围，仍有价值
        ensureIndexes(COLLECTION_COMMENT, List.of(byUser, byPublish, byParent));
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
