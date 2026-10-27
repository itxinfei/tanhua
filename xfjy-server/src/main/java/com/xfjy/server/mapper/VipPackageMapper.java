package com.xfjy.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xfjy.service.pojo.VipPackage;

/**
 * VIP 套餐 Mapper（admin 管理端配置读写，业务查询仍由 pay-service 提供）
 */
public interface VipPackageMapper extends BaseMapper<VipPackage> {
}