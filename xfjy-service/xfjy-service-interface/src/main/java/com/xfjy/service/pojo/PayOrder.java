package com.xfjy.service.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 支付订单
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_pay_order")
public class PayOrder implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 订单号（全局唯一，规则：VIP + yyyyMMdd + Redis 自增序列） */
    private String orderNo;
    /** 下单用户 id */
    private Long userId;
    /** 套餐 id */
    private Long packageId;
    /** 套餐名称（冗余，避免套餐改名影响历史订单展示） */
    private String packageName;
    /** 支付金额，单位：分 */
    private Long amount;
    /** 订单状态：PENDING-待支付，PAID-已支付，EXPIRED-已过期，CANCELLED-已取消 */
    private String status;
    /** 支付方式：SANDBOX-模拟沙箱，ALIPAY-支付宝，WECHAT-微信 */
    private String payType;
    /** 实际支付时间 */
    private Date paidTime;
    /** 订单过期时间（下单后 30 分钟） */
    private Date expireTime;
    private Date created;
    private Date updated;
}