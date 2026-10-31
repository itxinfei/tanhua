package com.xfjy.server.service;

import com.xfjy.service.api.PayApi;
import com.xfjy.service.pojo.ValueAddedService;
import com.xfjy.service.pojo.VipPackage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 后台 VIP 套餐与增值权益管理服务
 *
 * <p>套餐（tb_vip_package）支持完整 CRUD；权益（tb_value_added_service）仅支持
 * 编辑与上下架——serviceCode 为业务代码硬依赖，不可修改。
 * 所有数据库操作通过 PayApi 委托 pay-service 执行，server 模块不再持有 Mapper。</p>
 */
@Service
public class AdminVipService {

    @Autowired
    private PayApi payApi;

    /**
     * 分页查询 VIP 套餐
     */
    public Map<String, Object> queryPackagePage(Integer page, Integer pageSize,
                                                 String name, Integer status) {
        return this.payApi.queryPackagePage(page, pageSize, name, status);
    }

    /**
     * 新增 VIP 套餐
     */
    public Boolean createPackage(String name, Integer level, Long price, Long originalPrice,
                                  Integer durationDays, String description, Integer sort) {
        return this.payApi.createPackage(name, level, price, originalPrice, durationDays, description, sort);
    }

    /**
     * 编辑 VIP 套餐
     */
    public Boolean updatePackage(Long id, Map<String, Object> body) {
        return this.payApi.updatePackage(id, body);
    }

    /**
     * 套餐上架/下架
     */
    public Boolean togglePackage(Long id, Integer status) {
        return this.payApi.togglePackage(id, status);
    }

    /**
     * 分页查询增值权益
     */
    public Map<String, Object> queryBenefitPage(Integer page, Integer pageSize,
                                                 String name, Integer status) {
        return this.payApi.queryBenefitPage(page, pageSize, name, status);
    }

    /**
     * 编辑权益（仅允许改 vipLevelRequired/dailyLimit/description/sort，serviceCode 不可改）
     */
    public Boolean updateBenefit(Long id, Map<String, Object> body) {
        return this.payApi.updateBenefit(id, body);
    }

    /**
     * 权益启用/停用
     */
    public Boolean toggleBenefit(Long id, Integer status) {
        return this.payApi.toggleBenefit(id, status);
    }
}
