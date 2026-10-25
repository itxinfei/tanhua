package com.xfjy.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xfjy.service.pojo.PayOrder;

/**
 * 支付订单 Mapper（tb_pay_order 由 pay-service 独占）
 */
public interface PayOrderMapper extends BaseMapper<PayOrder> {
}