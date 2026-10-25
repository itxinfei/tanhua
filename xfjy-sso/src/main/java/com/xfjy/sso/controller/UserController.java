package com.xfjy.sso.controller;

import com.xfjy.sso.pojo.User;
import com.xfjy.sso.service.LoginLimitService;
import com.xfjy.sso.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<Void> sendCode(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String phone = body.get("phone");
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            return ResponseEntity.badRequest().build();
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
     */
    @PostMapping("/user/loginVerification")
    public ResponseEntity<Map<String, Object>> loginVerification(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String phone = body.get("phone");
        if (!this.loginLimitService.allowByIp(resolveClientIp(request))) {
            return ResponseEntity.status(429).build();
        }
        if (!this.loginLimitService.allowByPhone(phone)) {
            return ResponseEntity.status(423).build();
        }
        Map<String, Object> result = this.userService.loginVerification(phone, body.get("code"));
        if (result == null) {
            // 登录失败（验证码错误或账号封禁），计入失败次数
            this.loginLimitService.recordLoginFailure(phone);
            return ResponseEntity.status(401).build();
        }
        this.loginLimitService.recordLoginSuccess(phone);
        return ResponseEntity.ok(result);
    }

    /**
     * 根据 token 查询用户（xfjy-server TokenInterceptor 调用）
     * 无效 token 返回空 body（server 侧解析为 null）
     */
    @GetMapping("/user/{token}")
    public ResponseEntity<User> queryUserByToken(@PathVariable String token) {
        User user = this.userService.queryUserByToken(token);
        if (user == null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(user);
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
}
