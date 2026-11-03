package com.xfjy.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.pojo.Admin;
import com.xfjy.server.service.AdminAccountService;
import com.xfjy.server.utils.AdminThreadLocal;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台管理员账号管理Controller（仅SUPER_ADMIN可操作）
 */
@RestController
@RequireRole("SUPER_ADMIN")
@RequestMapping("admin/accounts")
public class AdminAccountController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAccountController.class);

    @Autowired
    private AdminAccountService adminAccountService;

    /**
     * 分页查询管理员列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> queryAdminPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        try {
            Map<String, Object> result = this.adminAccountService.queryAdminPage(page, pageSize, keyword);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 新增管理员
     */
    @PostMapping
    public ResponseEntity<Void> createAdmin(@RequestBody Map<String, String> param) {
        if (!isSuperAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            Boolean success = this.adminAccountService.createAdmin(
                    param.get("username"), param.get("password"),
                    param.get("nickname"), param.get("role"));
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 编辑管理员
     */
    @PutMapping("{id}")
    public ResponseEntity<Map<String, String>> updateAdmin(@PathVariable("id") Long id,
                                             @RequestBody Map<String, String> param) {
        if (!isSuperAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String role = param.get("role");
        if (StringUtils.isNotBlank(role) && !"SUPER_ADMIN".equals(role) && isCurrentAdmin(id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能修改自己的角色"));
        }
        try {
            Boolean success = this.adminAccountService.updateAdmin(
                    id, param.get("nickname"), role, param.get("avatar"));
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.badRequest().body(Map.of("message", "保存失败，请检查参数"));
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 禁用/启用管理员
     */
    @PutMapping("{id}/status")
    public ResponseEntity<Map<String, String>> toggleStatus(@PathVariable("id") Long id,
                                              @RequestBody Map<String, String> param) {
        if (!isSuperAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if ("DISABLED".equals(param.get("status")) && isCurrentAdmin(id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能禁用自己的账号"));
        }
        try {
            Boolean success = this.adminAccountService.toggleStatus(id, param.get("status"));
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.badRequest().body(Map.of("message", "操作失败，超级管理员不可禁用"));
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 重置管理员密码
     */
    @PutMapping("{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable("id") Long id,
                                               @RequestBody Map<String, String> param) {
        if (!isSuperAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            Boolean success = this.adminAccountService.resetPassword(id, param.get("newPassword"));
            if (Boolean.TRUE.equals(success)) {
                // 重置密码后强制该账号已有会话失效，需使用新密码重新登录
                StpUtil.logout("admin-" + id);
                return ResponseEntity.ok().build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 校验当前登录管理员是否为超级管理员
     */
    private boolean isSuperAdmin() {
        Admin admin = AdminThreadLocal.get();
        return admin != null && "SUPER_ADMIN".equals(admin.getRole());
    }

    /**
     * 目标账号是否为当前登录管理员
     */
    private boolean isCurrentAdmin(Long id) {
        Admin admin = AdminThreadLocal.get();
        return admin != null && id != null && id.equals(admin.getId());
    }
}