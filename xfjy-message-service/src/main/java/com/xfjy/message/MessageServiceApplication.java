package com.xfjy.message;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 消息/IM 服务（xfjy-message-service，18086）
 *
 * 承载环信 IM 集成契约实现（HuanXinApi）：
 * - 好友关系注册到环信
 * - 环信透传消息下发
 */
@SpringBootApplication(scanBasePackages = "com.xfjy")
public class MessageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageServiceApplication.class, args);
    }
}