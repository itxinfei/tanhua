package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.service.AdminUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

/**
 * 后台用户管理Controller
 */
@RestController
@RequireRole({"SUPER_ADMIN", "ADMIN"})
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
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            Map<String, Object> result = this.adminUserService.queryUserPage(page, pageSize, keyword, gender, status, startDate, endDate);
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

    /**
     * 批量封禁用户
     */
    @PutMapping("/batch-freeze")
    public ResponseEntity<Map<String, Object>> batchFreezeUser(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> userIds = (List<Long>) body.get("userIds");
            String reason = (String) body.get("reason");
            Map<String, Object> result = this.adminUserService.batchFreezeUser(userIds, reason);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 批量解封用户
     */
    @PutMapping("/batch-unfreeze")
    public ResponseEntity<Map<String, Object>> batchUnfreezeUser(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> userIds = (List<Long>) body.get("userIds");
            Map<String, Object> result = this.adminUserService.batchUnfreezeUser(userIds);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 查询用户会话列表（弹窗「聊天记录」tab 左侧）
     */
    @GetMapping("{id}/chat-sessions")
    public ResponseEntity<Map<String, Object>> queryChatSessions(@PathVariable("id") Long id) {
        try {
            List<Map<String, Object>> sessions = this.adminUserService.queryChatSessions(id);
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("total", sessions.size());
            result.put("list", sessions);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 查询与某用户的聊天记录（弹窗「聊天记录」tab 右侧）
     */
    @GetMapping("{id}/chat-messages")
    public ResponseEntity<Map<String, Object>> queryChatMessages(
            @PathVariable("id") Long id,
            @RequestParam("contactId") String contactId,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            Map<String, Object> result = this.adminUserService.queryChatMessages(id, contactId, pageSize);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}