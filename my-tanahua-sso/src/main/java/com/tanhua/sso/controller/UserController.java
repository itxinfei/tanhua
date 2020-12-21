package com.tanhua.sso.controller;

import com.tanhua.sso.pojo.User;
import com.tanhua.sso.service.UserService;
import com.tanhua.sso.vo.ErrorResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户登录
 */
@SuppressWarnings("unchecked")
@RequestMapping("user")
@RestController
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 登录
     *
     * @param param
     * @return
     */
    @PostMapping("loginVerification")
    public ResponseEntity<Object> login(@RequestBody Map<String, String> param) {
        try {
            //获取手机号
            String mobile = param.get("phone");
            //获取验证码
            String code = param.get("verificationCode");
            String token = this.userService.login(mobile, code);
            System.out.println("登录的token:" + token);
            if (StringUtils.isNotEmpty(token)) {
                // 登录成功
                String[] ss = StringUtils.split(token, '|');
                Boolean isNew = Boolean.valueOf(ss[0]);
                String tokenStr = ss[1];

                Map<String, Object> result = new HashMap<String, Object>();
                result.put("isNew", isNew);
                result.put("token", tokenStr);
                return ResponseEntity.ok(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        ErrorResult.ErrorResultBuilder builder = ErrorResult.builder().errCode("000000").errMessage("登录失败");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(builder.build());
    }

    /**
     * 根据token查询用户数据
     *
     * @param token
     * @return
     */
    @GetMapping("{token}")
    public User queryUserByToken(@PathVariable("token") String token) {
        return this.userService.queryUserByToken(token);
    }
}
