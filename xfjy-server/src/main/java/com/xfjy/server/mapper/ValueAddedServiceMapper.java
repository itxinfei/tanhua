package com.xfjy.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xfjy.service.pojo.ValueAddedService;

/**
 * 增值服务权益 Mapper（admin 管理端配置读写，业务校验仍由 pay-service 提供）
 */
public interface ValueAddedServiceMapper extends BaseMapper<ValueAddedService> {
}