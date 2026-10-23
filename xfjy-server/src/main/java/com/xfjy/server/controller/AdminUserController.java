package com.xfjy.server.controller;

import com.xfjy.server.service.AdminUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台用户管理Controller
 */
@RestController
@RequestMapping("admin/users")
public class AdminUserController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminUserController.class);

    @Autowired
    private AdminUserService adminUserService;

    /**
     * 分页查询用户列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> queryUserPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String status) {
        try {
            Map<String, Object> result = this.adminUserService.queryUserPage(page, pageSize, keyword, gender, status);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 查询用户详情
     */
    @GetMapping("{id}")
    public ResponseEntity<Map<String, Object>> queryUserDetail(@PathVariable("id") Long id) {
        try {
            Map<String, Object> result = this.adminUserService.queryUserDetail(id);
            if (result == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 封禁用户
     */
    @PutMapping("{id}/freeze")
    public ResponseEntity<Void> freezeUser(@PathVariable("id") Long id,
                                            @RequestBody Map<String, String> param) {
        try {
            String reason = param.get("reason");
            Boolean success = this.adminUserService.freezeUser(id, reason);
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 解封用户
     */
    @PutMapping("{id}/unfreeze")
    public ResponseEntity<Void> unfreezeUser(@PathVariable("id") Long id) {
        try {
            Boolean success = this.adminUserService.unfreezeUser(id);
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 重置用户密码
     */
    @PutMapping("{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable("id") Long id) {
        try {
            Boolean success = this.adminUserService.resetPassword(id);
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}