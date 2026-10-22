package com.xfjy.video;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 小视频服务（微服务化阶段三）
 * 承接自 xfjy-service-core：视频(Video)/关注作者(FollowUser)
 */
@SpringBootApplication(scanBasePackages = "com.xfjy")
public class VideoApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoApplication.class, args);
    }
}
