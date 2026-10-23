package com.xfjy.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * 用户关系域服务（微服务化阶段二）
 * 承接自 xfjy-service-core：好友(Users)、喜欢(UserLike)、访客(Visitors)
 * 阶段三新增：用户信息(UserInfo/tb_user_info)契约化实现
 */
@SpringBootApplication(scanBasePackages = "com.xfjy")
@MapperScan("com.xfjy.user.mapper")
@EnableCaching
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
