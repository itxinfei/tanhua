package com.xfjy.server.controller;

import com.xfjy.server.service.AdminVipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台 VIP 套餐与增值权益管理接口
 */
@RestController
@RequestMapping("admin/vip")
public class AdminVipController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminVipController.class);

    @Autowired
    private AdminVipService adminVipService;

    /**
     * GET /admin/vip/packages — 分页查询套餐
     */
    @GetMapping("packages")
    public ResponseEntity queryPackagePage(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                           @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                           @RequestParam(value = "name", required = false) String name,
                                           @RequestParam(value = "status", required = false) Integer status) {
        try {
            return ResponseEntity.ok(this.adminVipService.queryPackagePage(page, pageSize, name, status));
        } catch (Exception e) {
            LOGGER.error("查询VIP套餐列表失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "查询套餐列表失败"));
        }
    }

    /**
     * POST /admin/vip/packages — 新增套餐
     */
    @PostMapping("packages")
    public ResponseEntity createPackage(@RequestBody Map<String, Object> body) {
        try {
            String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
            Integer level = body.get("level") == null ? null : Integer.valueOf(String.valueOf(body.get("level")));
            Long price = body.get("price") == null ? null : Long.valueOf(String.valueOf(body.get("price")));
            Long originalPrice = body.get("originalPrice") == null ? null
                    : Long.valueOf(String.valueOf(body.get("originalPrice")));
            Integer durationDays = body.get("durationDays") == null ? null
                    : Integer.valueOf(String.valueOf(body.get("durationDays")));
            String description = body.get("description") == null ? null : String.valueOf(body.get("description"));
            Integer sort = body.get("sort") == null ? null : Integer.valueOf(String.valueOf(body.get("sort")));
            Boolean success = this.adminVipService.createPackage(
                    name, level, price, originalPrice, durationDays, description, sort);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "新增成功"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "新增失败，参数不完整"));
        } catch (Exception e) {
            LOGGER.error("新增VIP套餐失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "新增套餐失败"));
        }
    }

    /**
     * PUT /admin/vip/packages/{id} — 编辑套餐
     */
    @PutMapping("packages/{id}")
    public ResponseEntity updatePackage(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        try {
            Boolean success = this.adminVipService.updatePackage(id, body);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "编辑成功"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "编辑失败，记录不存在"));
        } catch (Exception e) {
            LOGGER.error("编辑VIP套餐失败, id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("message", "编辑套餐失败"));
        }
    }

    /**
     * PUT /admin/vip/packages/{id}/status — 套餐上架/下架
     */
    @PutMapping("packages/{id}/status")
    public ResponseEntity togglePackage(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        try {
            Integer status = body.get("status") == null ? null : Integer.valueOf(body.get("status"));
            Boolean success = this.adminVipService.togglePackage(id, status);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "操作成功"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "操作失败，记录不存在"));
        } catch (Exception e) {
            LOGGER.error("上下架VIP套餐失败, id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("message", "上下架套餐失败"));
        }
    }

    /**
     * GET /admin/vip/benefits — 分页查询权益
     */
    @GetMapping("benefits")
    public ResponseEntity queryBenefitPage(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                           @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                           @RequestParam(value = "name", required = false) String name,
                                           @RequestParam(value = "status", required = false) Integer status) {
        try {
            return ResponseEntity.ok(this.adminVipService.queryBenefitPage(page, pageSize, name, status));
        } catch (Exception e) {
            LOGGER.error("查询权益列表失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "查询权益列表失败"));
        }
    }

    /**
     * PUT /admin/vip/benefits/{id} — 编辑权益（serviceCode 不可改）
     */
    @PutMapping("benefits/{id}")
    public ResponseEntity updateBenefit(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        try {
            Boolean success = this.adminVipService.updateBenefit(id, body);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "编辑成功"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "编辑失败，记录不存在"));
        } catch (Exception e) {
            LOGGER.error("编辑权益失败, id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("message", "编辑权益失败"));
        }
    }

    /**
     * PUT /admin/vip/benefits/{id}/status — 权益启用/停用
     */
    @PutMapping("benefits/{id}/status")
    public ResponseEntity toggleBenefit(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        try {
            Integer status = body.get("status") == null ? null : Integer.valueOf(body.get("status"));
            Boolean success = this.adminVipService.toggleBenefit(id, status);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "操作成功"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "操作失败，记录不存在"));
        } catch (Exception e) {
            LOGGER.error("启停权益失败, id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("message", "启停权益失败"));
        }
    }
}