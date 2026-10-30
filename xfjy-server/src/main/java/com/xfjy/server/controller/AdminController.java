package com.xfjy.server.controller;

import com.xfjy.server.pojo.Admin;
import com.xfjy.server.service.AdminService;
import com.xfjy.server.utils.AdminThreadLocal;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理员账户Controller：登录 / 当前信息 / 修改密码 / 登出
 */
@RestController
@RequestMapping("admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    /**
     * 管理员登录
     */
    @PostMapping("login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> param) {
        String username = param.get("username");
        String password = param.get("password");
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Map<String, Object> result = this.adminService.login(username, password);
        if (result == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 401);
            error.put("message", "账号或密码错误");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 获取当前登录管理员信息
     */
    @GetMapping("info")
    public ResponseEntity<Admin> info() {
        Admin admin = AdminThreadLocal.get();
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(admin);
    }

    /**
     * 修改密码
     */
    @PutMapping("password")
    public ResponseEntity<Void> changePassword(@RequestBody Map<String, String> param) {
        Admin admin = AdminThreadLocal.get();
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String oldPassword = param.get("oldPassword");
        String newPassword = param.get("newPassword");
        if (StringUtils.isBlank(oldPassword) || StringUtils.isBlank(newPassword)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // 校验旧密码
        Admin dbAdmin = this.adminService.queryAdminById(admin.getId());
        if (dbAdmin == null || !DigestUtils.md5Hex(oldPassword).equals(dbAdmin.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // 更新新密码
        dbAdmin.setPassword(DigestUtils.md5Hex(newPassword));
        this.adminService.updateAdmin(dbAdmin);
        return ResponseEntity.ok().build();
    }

    /**
     * 退出登录
     */
    @PostMapping("logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            this.adminService.logout(token);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * 修改个人资料（昵称/头像）
     */
    @PutMapping("profile")
    public ResponseEntity<Admin> updateProfile(@RequestBody Map<String, String> param) {
        Admin admin = AdminThreadLocal.get();
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String nickname = param.get("nickname");
        String avatar = param.get("avatar");
        if (StringUtils.isBlank(nickname)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Admin dbAdmin = this.adminService.queryAdminById(admin.getId());
        if (dbAdmin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        dbAdmin.setNickname(nickname);
        dbAdmin.setAvatar(avatar);
        this.adminService.updateAdmin(dbAdmin);
        return ResponseEntity.ok(dbAdmin);
    }
}