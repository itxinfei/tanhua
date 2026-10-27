package com.xfjy.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xfjy.service.pojo.PayOrder;

/**
 * 支付订单 Mapper（admin 管理端只读查询，业务写入仍由 pay-service 独占）
 */
public interface PayOrderMapper extends BaseMapper<PayOrder> {
}