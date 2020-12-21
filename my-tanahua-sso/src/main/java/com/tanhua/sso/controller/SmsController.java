package com.tanhua.sso.controller;

import com.tanhua.sso.service.SmsService;
import com.tanhua.sso.vo.ErrorResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RequestMapping("user")
@RestController
public class SmsController {

    @Autowired
    private SmsService smsService;
    /**
     * 这里计划使用阿里的短信平台
     */
//    @Autowired
//    private AliSmsService aliSmsService;

    /**
     * 发送验证码接口
     *
     * @param param
     * @return
     */
    @PostMapping("login")
    public ResponseEntity<Object> sendCheckCode(@RequestBody Map<String, Object> param) {
        ErrorResult.ErrorResultBuilder resultBuilder = ErrorResult.builder().errCode("000000").errMessage("发送短信验证码失败");
        try {
            String phone = String.valueOf(param.get("phone"));
            System.out.println("登录业务逻辑开始，后台收到用户的手机号：" + phone);

            Map<String, Object> sendCheckCode = this.smsService.sendCheckCode(phone);
            int code = ((Integer) sendCheckCode.get("code")).intValue();
            if (code == 3) {
                return ResponseEntity.ok(null);
            } else if (code == 1) {
                resultBuilder.errCode("000001").errMessage(sendCheckCode.get("msg").toString());
            }
        } catch (Exception e) {

        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultBuilder.build());
    }

}
