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
 * 小视频推荐（mongo-spark-connector 10.x API）
 */
public class SparkVideo {

    public static void main(String[] args) throws Exception {

        // 读取外部的配置文件
        InputStream inputStream = SparkVideo.class.getClassLoader().getResourceAsStream("app.properties");
        Properties properties = new Properties();
        properties.load(inputStream);

        // 构建SparkSession，设置MongoDB读取连接
        SparkConf sparkConf = new SparkConf()
                .setAppName("SparkVideo")
                .setMaster("local[*]")
                .set("spark.mongodb.read.connection.uri", properties.getProperty("spark.mongodb.connection.uri"));

        SparkSession sparkSession = SparkSession.builder().config(sparkConf).getOrCreate();

        // 通过 DataSource 方式加载 MongoDB 中的数据（10.x API）
        Dataset<Row> df = sparkSession.read()
                .format("mongodb")
                .option("database", properties.getProperty("spark.mongodb.database"))
                .option("collection", properties.getProperty("spark.mongodb.collection.video"))
                .load();
        JavaRDD<Row> rdd = df.toJavaRDD();

        // 提取字段到数组：[userId, videoId, score, date]
        JavaRDD<Object[]> dataRdd = rdd.map(row -> new Object[]{
                row.getAs("userId"),
                row.getAs("videoId"),
                row.getAs("score"),
                row.getAs("date")
        });

        // 同一用户对同一小视频的多次操作，需要合并分数
        JavaRDD<Object[]> values = dataRdd.mapToPair(arr ->
                new Tuple2<>(arr[0] + "_" + arr[1], arr)
        ).reduceByKey((v1, v2) -> {
            v1[2] = ((Number) v1[2]).doubleValue() + ((Number) v2[2]).doubleValue();
            return v1;
        }).values();

        // 得到数据中的用户id集合
        List<Long> userIdList = dataRdd.map(arr -> ((Number) arr[0]).longValue()).distinct().collect();

        // 按照日期对10取模作为key，Rating对象作为value
        JavaPairRDD<Long, Rating> ratings = values.mapToPair(arr -> {
            int user = ((Number) arr[0]).intValue();
            int product = ((Number) arr[1]).intValue();
            double score = ((Number) arr[2]).doubleValue();
            long date = ((Number) arr[3]).longValue();
            return new Tuple2<>(date % 10, new Rating(user, product, score));
        });

        // 通过MLlib模型进行推荐，获取最优推荐模型
        MLlibRecommend mLlibRecommend = new MLlibRecommend();
        MatrixFactorizationModel bestModel = mLlibRecommend.bestModel(ratings);

        // 构建Redis环境
        String redisClusterNodes = properties.getProperty("redis.cluster.nodes");
        String[] redisNodes = redisClusterNodes.split(",");
        Set<HostAndPort> nodes = new HashSet<>();
        for (String redisNode : redisNodes) {
            String[] hostAndPorts = redisNode.split(":");
            nodes.add(new HostAndPort(hostAndPorts[0], Integer.valueOf(hostAndPorts[1])));
        }
        JedisCluster jedisCluster = new JedisCluster(nodes);

        // 分别对每一个用户进行推荐，推荐20个小视频信息
        for (Long userId : userIdList) {
            Rating[] recommendProducts = bestModel.recommendProducts(userId.intValue(), 20);

            List<Integer> products = new ArrayList<>();
            for (Rating rating : recommendProducts) {
                products.add(rating.product());
            }

            // 存储到redis
            String key = "QUANZI_VIDEO_RECOMMEND_" + userId;
            jedisCluster.set(key, StringUtils.join(products, ','));
        }

        // 关闭连接
        jedisCluster.close();
        sparkSession.close();
    }
}