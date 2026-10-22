package com.xfjy.sso.controller;

import com.xfjy.sso.pojo.User;
import com.xfjy.sso.service.UserService;
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
 */
@RestController
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 发送短信验证码
     */
    @PostMapping("/user/sendCode")
    public ResponseEntity<Void> sendCode(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            return ResponseEntity.badRequest().build();
        }
        this.userService.sendCode(phone);
        return ResponseEntity.ok().build();
    }

    /**
     * 手机号 + 验证码登录/注册
     */
    @PostMapping("/user/loginVerification")
    public ResponseEntity<Map<String, Object>> loginVerification(@RequestBody Map<String, String> body) {
        Map<String, Object> result = this.userService.loginVerification(body.get("phone"), body.get("code"));
        if (result == null) {
            return ResponseEntity.status(401).build();
        }
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
}
