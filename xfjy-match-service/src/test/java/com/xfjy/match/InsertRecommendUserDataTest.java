package com.xfjy.match;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 一次性数据工具：向 MongoDB xfjy.recommend_user 插入 toUserId=104 的测试推荐数据。
 * 直连 MongoDB，不启动 Spring 上下文。运行：mvn -pl xfjy-match-service test -Dtest=InsertRecommendUserDataTest
 */
class InsertRecommendUserDataTest {

    private static final String MONGO_URI = "mongodb://192.168.20.130:27017";

    @Test
    void insertRecommendUserData() {
        try (MongoClient client = MongoClients.create(MONGO_URI)) {
            MongoDatabase db = client.getDatabase("xfjy");
            MongoCollection<Document> col = db.getCollection("recommend_user");

            // 幂等：先清掉 toUserId=104 的旧数据
            col.deleteMany(new Document("toUserId", 104L));

            List<Document> docs = Arrays.asList(
                    doc(1L, 88.0), doc(2L, 76.0), doc(3L, 91.0), doc(4L, 82.0), doc(5L, 95.0),
                    doc(6L, 68.0), doc(7L, 73.0), doc(8L, 98.0), doc(9L, 85.0), doc(10L, 79.0));
            col.insertMany(docs);

            long count = col.countDocuments(new Document("toUserId", 104L));
            System.out.println("[InsertRecommendUserDataTest] inserted count=" + count);
        }
    }

    private Document doc(Long userId, Double score) {
        return new Document("userId", userId)
                .append("toUserId", 104L)
                .append("score", score)
                .append("date", "2026-10-24");
    }
}