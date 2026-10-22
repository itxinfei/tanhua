package com.xfjy.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户关系域服务（微服务化阶段二）
 * 承接自 xfjy-service-core：好友(Users)、喜欢(UserLike)、访客(Visitors)
 */
@SpringBootApplication(scanBasePackages = "com.xfjy")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
