package com.xfjy.pay;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 支付服务（微服务化阶段四）
 * 承接 VIP 套餐、订单、模拟支付沙箱、VIP 开通闭环。
 * 生产环境接入真实支付网关时，仅需替换 PayService 中的沙箱确认逻辑。
 */
@SpringBootApplication(scanBasePackages = "com.xfjy")
@MapperScan("com.xfjy.pay.mapper")
public class PayApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayApplication.class, args);
    }
}