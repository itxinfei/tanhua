package com.xfjy.spark.mongo;

import org.apache.commons.lang3.StringUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.mllib.recommendation.MatrixFactorizationModel;
import org.apache.spark.mllib.recommendation.Rating;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import scala.Tuple2;

import java.io.InputStream;
import java.util.*;

/**
 * 圈子动态推荐（mongo-spark-connector 10.x API）
 */
public class SparkQunaZi {

    public static void main(String[] args) throws Exception {

        // 加载外部的配置文件，app.properties
        InputStream inputStream = SparkQunaZi.class.getClassLoader().getResourceAsStream("app.properties");
        Properties properties = new Properties();
        properties.load(inputStream);

        // 构建SparkSession，设置MongoDB读取连接
        SparkConf sparkConf = new SparkConf()
                .setAppName("SparkQunaZi")
                .setMaster("local[*]")
                .set("spark.mongodb.read.connection.uri", properties.getProperty("spark.mongodb.connection.uri"));

        SparkSession sparkSession = SparkSession.builder().config(sparkConf).getOrCreate();

        // 通过 DataSource 方式加载 MongoDB 中的数据（10.x API）
        Dataset<Row> df = sparkSession.read()
                .format("mongodb")
                .option("database", properties.getProperty("spark.mongodb.database"))
                .option("collection", properties.getProperty("spark.mongodb.collection.quanzi"))
                .load();
        JavaRDD<Row> rdd = df.toJavaRDD();

        // 提取字段到数组：[userId, publishId, score, date]
        JavaRDD<Object[]> dataRdd = rdd.map(row -> new Object[]{
                row.getAs("userId"),
                row.getAs("publishId"),
                row.getAs("score"),
                row.getAs("date")
        });

        // 同一用户对相同动态的多次操作，需要合并分数
        JavaRDD<Object[]> values = dataRdd.mapToPair(arr ->
                new Tuple2<>(arr[0] + "_" + arr[1], arr)
        ).reduceByKey((v1, v2) -> {
            v1[2] = ((Number) v1[2]).doubleValue() + ((Number) v2[2]).doubleValue();
            return v1;
        }).values();

        // 用户列表
        List<Long> userIdList = dataRdd.map(arr -> ((Number) arr[0]).longValue()).distinct().collect();

        // 按照日期对10取模作为key，Rating对象作为value
        JavaPairRDD<Long, Rating> ratings = values.mapToPair(arr -> {
            long date = ((Number) arr[3]).longValue();
            int userId = ((Number) arr[0]).intValue();
            int publishId = ((Number) arr[1]).intValue();
            double score = ((Number) arr[2]).doubleValue();
            return new Tuple2<>(date % 10, new Rating(userId, publishId, score));
        });

        // MLlib训练最佳推荐模型
        MLlibRecommend mLlibRecommend = new MLlibRecommend();
        MatrixFactorizationModel bestModel = mLlibRecommend.bestModel(ratings);

        // 连接redis，做存储
        String redisNodesStr = properties.getProperty("redis.cluster.nodes");
        String[] redisNodesStrs = StringUtils.split(redisNodesStr, ',');
        Set<HostAndPort> nodes = new HashSet<>();
        for (String nodesStr : redisNodesStrs) {
            String[] ss = StringUtils.split(nodesStr, ':');
            nodes.add(new HostAndPort(ss[0], Integer.valueOf(ss[1])));
        }
        JedisCluster jedisCluster = new JedisCluster(nodes);

        for (Long userId : userIdList) {
            Rating[] recommendProducts = bestModel.recommendProducts(userId.intValue(), 20);

            List<Integer> products = new ArrayList<>();
            for (Rating product : recommendProducts) {
                products.add(product.product());
            }

            String key = "QUANZI_PUBLISH_RECOMMEND_" + userId;
            jedisCluster.set(key, StringUtils.join(products, ','));
        }

        // 关闭
        jedisCluster.close();
        sparkSession.close();
    }
}