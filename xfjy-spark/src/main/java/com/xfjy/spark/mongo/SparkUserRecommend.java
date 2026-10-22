package com.xfjy.spark.mongo;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.mllib.recommendation.MatrixFactorizationModel;
import org.apache.spark.mllib.recommendation.Rating;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import scala.Tuple2;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 用户推荐（读取MySQL，写入MongoDB，mongo-spark-connector 10.x API）
 */
public class SparkUserRecommend {

    public static void main(String[] args) throws Exception {

        // 加载外部的配置文件，app.properties
        InputStream inputStream = SparkUserRecommend.class.getClassLoader().getResourceAsStream("app.properties");
        Properties properties = new Properties();
        properties.load(inputStream);

        // 构建SparkSession，设置MongoDB写入连接
        SparkConf sparkConf = new SparkConf()
                .setAppName("SparkUserRecommend")
                .setMaster("local[*]")
                .set("spark.mongodb.write.connection.uri", properties.getProperty("spark.mongodb.connection.uri"));

        SparkSession sparkSession = SparkSession.builder().config(sparkConf).getOrCreate();

        // 加载mysql数据
        String url = properties.getProperty("jdbc.url");

        // 设置数据库连接信息
        Properties connectionProperties = new Properties();
        connectionProperties.put("driver", properties.getProperty("jdbc.driver-class-name"));
        connectionProperties.put("user", properties.getProperty("jdbc.username"));
        connectionProperties.put("password", properties.getProperty("jdbc.password"));

        JavaRDD<Row> userInfoRdd = sparkSession.read().jdbc(url, "tb_user_info", connectionProperties).toJavaRDD();

        // 用户列表
        List<Long> userIds = userInfoRdd.map(v -> v.getLong(1)).collect();

        // 计算出这张表数据的笛卡尔积
        JavaPairRDD<Row, Row> cartesian = userInfoRdd.cartesian(userInfoRdd);

        // 计算用户的相似度
        JavaPairRDD<Long, Rating> javaPairRDD = cartesian.mapToPair(row -> {
            Row row1 = row._1();
            Row row2 = row._2();

            Long userId1 = row1.getLong(1);
            Long userId2 = row2.getLong(1);

            Long key = userId1 + userId2 + RandomUtils.nextLong();

            // 自己与自己对比
            if (userId1.longValue() == userId2.longValue()) {
                return new Tuple2<>(key % 10, new Rating(userId1.intValue(), userId2.intValue(), 0));
            }

            double score = 0;

            // 计算年龄差
            int ageDiff = Math.abs(row1.getInt(6) - row2.getInt(6));
            if (ageDiff <= 2) {
                score += 30;
            } else if (ageDiff >= 3 && ageDiff <= 5) {
                score += 20;
            } else if (ageDiff > 5 && ageDiff <= 10) {
                score += 10;
            }

            // 计算性别
            if (row1.getInt(5) != row2.getInt(5)) {
                score += 30;
            }

            // 计算城市
            String city1 = StringUtils.substringBefore(row1.getString(8), "-");
            String city2 = StringUtils.substringBefore(row2.getString(8), "-");
            if (StringUtils.equals(city1, city2)) {
                score += 20;
            }

            // 计算学历
            String edu1 = row1.getString(7);
            String edu2 = row2.getString(7);
            if (StringUtils.equals(edu1, edu2)) {
                score += 20;
            }

            return new Tuple2<>(key % 10, new Rating(userId1.intValue(), userId2.intValue(), score));
        });

        // MLlib进行计算最佳的推荐模型
        MLlibRecommend mLlibRecommend = new MLlibRecommend();
        MatrixFactorizationModel bestModel = mLlibRecommend.bestModel(javaPairRDD);

        // 定义输出schema（MongoDB自动生成_id）
        StructType schema = new StructType(new StructField[]{
                StructField.apply("userId", DataTypes.IntegerType, false, Metadata.empty()),
                StructField.apply("toUserId", DataTypes.IntegerType, false, Metadata.empty()),
                StructField.apply("score", DataTypes.DoubleType, false, Metadata.empty()),
                StructField.apply("date", DataTypes.StringType, false, Metadata.empty())
        });

        // 为每个用户生成推荐结果，写入到MongoDB中
        for (Long userId : userIds) {
            Rating[] ratings = bestModel.recommendProducts(userId.intValue(), 50);

            List<Row> rows = new ArrayList<>();
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

            for (Rating rating : ratings) {
                // 得分，保留2位小数
                double score = BigDecimal.valueOf(rating.rating())
                        .setScale(2, RoundingMode.DOWN)
                        .doubleValue();
                rows.add(RowFactory.create(rating.product(), rating.user(), score, dateStr));
            }

            Dataset<Row> resultDf = sparkSession.createDataFrame(rows, schema);

            // 通过 DataSource 方式写入 MongoDB（10.x API）
            resultDf.write()
                    .format("mongodb")
                    .option("database", properties.getProperty("spark.mongodb.database"))
                    .option("collection", properties.getProperty("spark.mongodb.collection.user"))
                    .mode("append")
                    .save();
        }

        sparkSession.close();
    }
}