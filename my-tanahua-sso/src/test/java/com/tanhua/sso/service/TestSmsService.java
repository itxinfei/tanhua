package com.tanhua.sso.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class TestSmsService {

    @Autowired
    private SmsService smsService;

    @Autowired
    private UserService userService;

    @Resource
    private RestTemplate restTemplate;

    @Test
    public void testSend() {
        String code = this.smsService.sendSms("15800807944");
        System.out.println(code);
    }

    @Test
    public void randomCode() {
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
        System.out.println(code);
    }

    @Test
    public void login() {
        String log = userService.login("17671787093", "123456");
        System.out.println(log);
    }

}
