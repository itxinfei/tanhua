package com.xfjy.video.config;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 「附近」依赖 video.geo 的 2dsphere 索引（$geoNear / nearSphere 都是硬依赖）。
 *
 * <p>项目未开启 Mongo 自动建索引，因此这里在启动期显式创建一次；
 * createIndexes 是幂等的：索引已存在且定义一致时直接返回成功。
 * 若线上已存在同名但定义冲突的索引，只记录 ERROR 并继续启动（不阻塞其它接口），
 * 由运维按日志处理——这就是附近查询的唯一硬前置条件。</p>
 */
@Component
public class VideoGeoIndexInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoGeoIndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    public VideoGeoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Document index = new Document("key", new Document("geo", "2dsphere"))
                    .append("name", "geo_2dsphere");
            Document command = new Document("createIndexes", "video")
                    .append("indexes", Collections.singletonList(index));
            this.mongoTemplate.getDb().runCommand(command);
            LOGGER.info("video.geo 2dsphere 索引已就绪（幂等创建）");
        } catch (Exception e) {
            LOGGER.error("创建 video.geo 2dsphere 索引失败，「附近」查询将不可用，请检查是否已有冲突索引", e);
        }
    }
}
