package com.xfjy.circle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 圈子动态服务（微服务化阶段三）
 * 承接自 xfjy-service-core：动态发布(Publish)/评论(Comment)/时间线(TimeLine)/相册(Album)
 */
@SpringBootApplication(scanBasePackages = "com.xfjy")
public class CircleApplication {

    public static void main(String[] args) {
        SpringApplication.run(CircleApplication.class, args);
    }
}
