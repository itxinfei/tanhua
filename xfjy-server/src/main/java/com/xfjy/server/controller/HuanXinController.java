package com.xfjy.server.controller;

import com.xfjy.server.pojo.User;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.server.vo.HuanXinUser;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("huanxin")
public class HuanXinController {

    @GetMapping("user")
    public ResponseEntity<HuanXinUser> queryUser(){
        User user = UserThreadLocal.get();

        HuanXinUser huanXinUser = new HuanXinUser();
        huanXinUser.setUsername(user.getId().toString());
        huanXinUser.setPassword(DigestUtils.md5Hex(user.getId() + "_xfjy"));

        return ResponseEntity.ok(huanXinUser);
    }

}
