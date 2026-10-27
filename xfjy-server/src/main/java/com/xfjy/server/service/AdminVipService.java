package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.service.pojo.ValueAddedService;
import com.xfjy.service.pojo.VipPackage;
import com.xfjy.server.mapper.ValueAddedServiceMapper;
import com.xfjy.server.mapper.VipPackageMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 后台 VIP 套餐与增值权益管理服务
 *
 * <p>套餐（tb_vip_package）支持完整 CRUD；权益（tb_value_added_service）仅支持
 * 编辑与上下架——serviceCode 为业务代码硬依赖，不可修改。</p>
 */
@Service
public class AdminVipService {

    @Autowired
    private VipPackageMapper vipPackageMapper;

    @Autowired
    private ValueAddedServiceMapper valueAddedServiceMapper;

    /**
     * 分页查询 VIP 套餐
     */
    public Map<String, Object> queryPackagePage(Integer page, Integer pageSize,
                                                 String name, Integer status) {
        QueryWrapper<VipPackage> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(name)) {
            wrapper.like("name", name);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByAsc("level");

        IPage<VipPackage> packagePage = this.vipPackageMapper.selectPage(new Page<>(page, pageSize), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", packagePage.getTotal());
        result.put("list", packagePage.getRecords());
        return result;
    }

    /**
     * 新增 VIP 套餐
     */
    public Boolean createPackage(String name, Integer level, Long price, Long originalPrice,
                                  Integer durationDays, String description, Integer sort) {
        if (StringUtils.isBlank(name) || level == null || price == null || durationDays == null) {
            return false;
        }
        VipPackage entity = new VipPackage();
        entity.setName(name);
        entity.setLevel(level);
        entity.setPrice(price);
        entity.setOriginalPrice(originalPrice);
        entity.setDurationDays(durationDays);
        entity.setDescription(description);
        entity.setSort(sort == null ? 0 : sort);
        entity.setStatus(1);
        return this.vipPackageMapper.insert(entity) > 0;
    }

    /**
     * 编辑 VIP 套餐
     */
    public Boolean updatePackage(Long id, Map<String, Object> body) {
        VipPackage entity = this.vipPackageMapper.selectById(id);
        if (entity == null) {
            return false;
        }
        if (body.containsKey("name") && body.get("name") != null) {
            entity.setName(String.valueOf(body.get("name")));
        }
        if (body.containsKey("level") && body.get("level") != null) {
            entity.setLevel(Integer.valueOf(String.valueOf(body.get("level"))));
        }
        if (body.containsKey("price") && body.get("price") != null) {
            entity.setPrice(Long.valueOf(String.valueOf(body.get("price"))));
        }
        // originalPrice 允许置空（null=不显示划线原价），只要 key 存在就更新
        if (body.containsKey("originalPrice")) {
            Object originalPrice = body.get("originalPrice");
            entity.setOriginalPrice(originalPrice == null ? null : Long.valueOf(String.valueOf(originalPrice)));
        }
        if (body.containsKey("durationDays") && body.get("durationDays") != null) {
            entity.setDurationDays(Integer.valueOf(String.valueOf(body.get("durationDays"))));
        }
        if (body.containsKey("description")) {
            entity.setDescription(body.get("description") == null ? null : String.valueOf(body.get("description")));
        }
        if (body.containsKey("sort") && body.get("sort") != null) {
            entity.setSort(Integer.valueOf(String.valueOf(body.get("sort"))));
        }
        return this.vipPackageMapper.updateById(entity) > 0;
    }

    /**
     * 套餐上架/下架
     */
    public Boolean togglePackage(Long id, Integer status) {
        VipPackage entity = this.vipPackageMapper.selectById(id);
        if (entity == null || status == null) {
            return false;
        }
        entity.setStatus(status);
        return this.vipPackageMapper.updateById(entity) > 0;
    }

    /**
     * 分页查询增值权益
     */
    public Map<String, Object> queryBenefitPage(Integer page, Integer pageSize,
                                                 String name, Integer status) {
        QueryWrapper<ValueAddedService> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(name)) {
            wrapper.like("name", name);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByAsc("vip_level_required");

        IPage<ValueAddedService> benefitPage = this.valueAddedServiceMapper.selectPage(new Page<>(page, pageSize), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", benefitPage.getTotal());
        result.put("list", benefitPage.getRecords());
        return result;
    }

    /**
     * 编辑权益（仅允许改 vipLevelRequired/dailyLimit/description/sort，serviceCode 不可改）
     */
    public Boolean updateBenefit(Long id, Map<String, Object> body) {
        ValueAddedService entity = this.valueAddedServiceMapper.selectById(id);
        if (entity == null) {
            return false;
        }
        if (body.containsKey("vipLevelRequired") && body.get("vipLevelRequired") != null) {
            entity.setVipLevelRequired(Integer.valueOf(String.valueOf(body.get("vipLevelRequired"))));
        }
        // dailyLimit 允许置空（null=不限次），只要 key 存在就更新
        if (body.containsKey("dailyLimit")) {
            Object dailyLimit = body.get("dailyLimit");
            entity.setDailyLimit(dailyLimit == null ? null : Integer.valueOf(String.valueOf(dailyLimit)));
        }
        if (body.containsKey("description")) {
            entity.setDescription(body.get("description") == null ? null : String.valueOf(body.get("description")));
        }
        if (body.containsKey("sort") && body.get("sort") != null) {
            entity.setSort(Integer.valueOf(String.valueOf(body.get("sort"))));
        }
        return this.valueAddedServiceMapper.updateById(entity) > 0;
    }

    /**
     * 权益启用/停用
     */
    public Boolean toggleBenefit(Long id, Integer status) {
        ValueAddedService entity = this.valueAddedServiceMapper.selectById(id);
        if (entity == null || status == null) {
            return false;
        }
        entity.setStatus(status);
        return this.valueAddedServiceMapper.updateById(entity) > 0;
    }
}