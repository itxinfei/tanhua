package com.xfjy.match;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 探花匹配服务（微服务化阶段三）
 * 承接自 xfjy-service-core：推荐用户(RecommendUser)/用户地理位置(UserLocation)/ES附近的人(ESUserLocation)
 */
@SpringBootApplication(scanBasePackages = "com.xfjy")
public class MatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchServiceApplication.class, args);
    }
}