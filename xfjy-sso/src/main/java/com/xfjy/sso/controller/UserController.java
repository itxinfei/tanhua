package com.xfjy.sso.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.xfjy.sso.pojo.User;
import com.xfjy.sso.service.LoginLimitService;
import com.xfjy.sso.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户认证接口（app 端 + xfjy-server 校验端契约）
 *
 * <p>风控：登录/发码接入 {@link LoginLimitService} 三层防线（IP 限流/账号锁定/发码限流）</p>
 */
@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private LoginLimitService loginLimitService;

    /**
     * 发送短信验证码
     *
     * <p>风控：同手机号 60s 内最多 1 次，同 IP 60s 内最多 10 次，超限 429</p>
     */
    @PostMapping("/user/sendCode")
    public ResponseEntity<Map<String, Object>> sendCode(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String phone = body.get("phone");
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            Map<String, Object> fail = new HashMap<>();
            fail.put("message", "请输入正确的手机号");
            return ResponseEntity.badRequest().body(fail);
        }
        if (!this.loginLimitService.allowByIp(resolveClientIp(request))
                || !this.loginLimitService.allowSendCode(phone)) {
            return ResponseEntity.status(429).build();
        }
        this.userService.sendCode(phone);
        return ResponseEntity.ok().build();
    }

    /**
     * 手机号 + 验证码登录/注册
     *
     * <p>风控：同 IP 60s 内最多 10 次（429），连续失败 5 次锁账号 15 分钟（423）</p>
     * <p>状态码：200 成功 / 401 验证码错误 / 403 已封禁（body 携带 reason）</p>
     */
    @PostMapping("/user/loginVerification")
    public ResponseEntity<Map<String, Object>> loginVerification(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String phone = body.get("phone");
        if (!this.loginLimitService.allowByIp(resolveClientIp(request))) {
            return ResponseEntity.status(429).build();
        }
        if (!this.loginLimitService.allowByPhone(phone)) {
            // 账号锁定：body 携带 message，前端展示精确文案（request 层 423 分支）
            Map<String, Object> locked = new HashMap<>();
            locked.put("message", "连续失败次数过多，账号已暂时锁定，请 15 分钟后重试");
            return ResponseEntity.status(423).body(locked);
        }
        Map<String, Object> result = this.userService.loginVerification(phone, body.get("code"));
        if (result == null) {
            // 登录失败（验证码错误/已注销），计入失败次数；已注销不暴露账号状态
            this.loginLimitService.recordLoginFailure(phone);
            // 携带 message：前端 401 分支区分「验证码错误」（展示文案）与「token 过期」（登出跳登录页）
            Map<String, Object> fail = new HashMap<>();
            fail.put("message", "验证码错误或已过期");
            return ResponseEntity.status(401).body(fail);
        }
        if ("FROZEN".equals(result.get("error"))) {
            // 已封禁：403 + 封禁原因（FR-AUTH-01 验收口径），不计入验证码失败次数
            Map<String, Object> fail = new HashMap<>();
            fail.put("error", "FROZEN");
            fail.put("reason", result.get("reason"));
            return ResponseEntity.status(403).body(fail);
        }
        this.loginLimitService.recordLoginSuccess(phone);
        return ResponseEntity.ok(result);
    }

    /**
     * 根据 token 查询用户（xfjy-server TokenInterceptor 调用）
     * token 通过 X-Token 请求头传递，避免出现在 URL 路径中被访问日志/反向代理日志记录。
     * 无效 token 返回空 body（server 侧解析为 null）
     */
    @GetMapping("/user/query")
    public ResponseEntity<User> queryUserByToken(@RequestHeader("X-Token") String token) {
        User user = this.userService.queryUserByToken(token);
        if (user == null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(user);
    }

    /**
     * 换绑手机号（需验证新手机号的短信验证码）
     *
     * <p>风控：同 IP 60s 内最多 10 次（429）</p>
     * <p>状态码：200 成功 / 400 格式错 / 401 token 失效 / 403 验证码错误 / 409 手机号已被占用</p>
     * <p>400/403/409 在 body 携带 message，前端 request 层直接展示；401 不带 message（走前端登出流程）</p>
     * <p>注意：验证码错误用 403 而非 401，避免前端 401 拦截器强制跳登录页</p>
     */
    @PutMapping("/user/phone")
    public ResponseEntity<Map<String, Object>> changePhone(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!this.loginLimitService.allowByIp(resolveClientIp(request))) {
            return ResponseEntity.status(429).build();
        }

        String newPhone = body.get("phone");
        String code = body.get("code");
        if (newPhone == null || !newPhone.matches("^1[3-9]\\d{9}$")
                || code == null || !code.matches("^\\d{6}$")) {
            Map<String, Object> fail = new HashMap<>();
            fail.put("message", "请输入正确的手机号或 6 位验证码");
            return ResponseEntity.badRequest().body(fail);
        }

        // 解析登录态（Sa-Token 未配 token-prefix，手动剥 Bearer，与 server 侧 TokenInterceptor 一致）
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        Object loginId = StpUtil.getLoginIdByToken(authHeader.substring(7));
        // 命名空间隔离：只接受 "user-" 前缀，后台 admin token 在此直接拒绝
        if (loginId == null || !loginId.toString().startsWith("user-")) {
            return ResponseEntity.status(401).build();
        }

        int result = this.userService.changePhone(Long.valueOf(loginId.toString().substring("user-".length())), newPhone, code);
        if (result == 1) {
            Map<String, Object> fail = new HashMap<>();
            fail.put("message", "验证码错误，请重新输入");
            return ResponseEntity.status(403).body(fail);
        }
        if (result == 2) {
            Map<String, Object> fail = new HashMap<>();
            fail.put("message", "该手机号已被其他用户注册");
            return ResponseEntity.status(409).body(fail);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * 解析客户端真实 IP：X-Forwarded-For 首段 → X-Real-IP → 远端地址
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * 内部接口：封禁用户（仅 xfjy-server 经内部网络调用，路径不含 /user 前缀故网关不路由，禁止外部直连）。
     */
    @PostMapping("/internal/user/freeze")
    public ResponseEntity<Boolean> internalFreeze(@RequestParam Long userId, @RequestParam String reason) {
        return ResponseEntity.ok(userService.freezeUser(userId, reason));
    }

    /**
     * 内部接口：解封用户（仅 xfjy-server 经内部网络调用，网关不路由，禁止外部直连）。
     */
    @PostMapping("/internal/user/unfreeze")
    public ResponseEntity<Boolean> internalUnfreeze(@RequestParam Long userId) {
        return ResponseEntity.ok(userService.unfreezeUser(userId));
    }

    /**
     * 内部接口：重置用户密码（仅 xfjy-server 经内部网络调用，网关不路由，禁止外部直连）。
     */
    @PostMapping("/internal/user/reset-password")
    public ResponseEntity<Boolean> internalResetPassword(@RequestParam Long userId, @RequestParam String newPassword) {
        return ResponseEntity.ok(userService.resetPassword(userId, newPassword));
    }

    /**
     * 内部接口：注销用户（软删除，仅 xfjy-server 经内部网络调用，网关不路由，禁止外部直连）。
     */
    @PostMapping("/internal/user/deactivate")
    public ResponseEntity<Boolean> internalDeactivate(@RequestParam Long userId) {
        return ResponseEntity.ok(userService.deactivateUser(userId));
    }
}
