package com.xfjy.recommend;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 查询 recommend_quanzi_ / recommend_video_ 集合数据，验证 MQ 消费链路是否写入评分数据。
 * 仅本地验证用，不入库不提交。
 */
class QueryRecommendDataTest {

    @Test
    void queryRecommendQuanZi() {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        try (MongoClient client = MongoClients.create("mongodb://192.168.20.130:27017")) {
            MongoDatabase db = client.getDatabase("xfjy");
            for (String name : new String[]{"recommend_quanzi_" + today, "recommend_video_" + today}) {
                MongoCollection<Document> col = db.getCollection(name);
                long count = col.countDocuments();
                System.out.println("=== " + name + " count=" + count + " ===");
                for (Document doc : col.find().limit(10)) {
                    System.out.println(doc.toJson());
                }
            }
            // Spark ALS 写入的推荐结果
            MongoCollection<Document> recommendUser = db.getCollection("recommend_user");
            System.out.println("=== recommend_user total=" + recommendUser.countDocuments() + " ===");
            for (Document doc : recommendUser.find(new Document("toUserId", 104))
                    .sort(new Document("score", -1)).limit(5)) {
                System.out.println(doc.toJson());
            }
        }
    }
}