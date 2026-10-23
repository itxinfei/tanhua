package com.xfjy.server.controller;

import com.xfjy.server.pojo.User;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.server.vo.HuanXinUser;
import com.xfjy.service.api.HuanXinApi;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("huanxin")
public class HuanXinController {

    private final HuanXinApi huanXinApi;

    public HuanXinController(HuanXinApi huanXinApi) {
        this.huanXinApi = huanXinApi;
    }

    @GetMapping("user")
    public ResponseEntity<HuanXinUser> queryUser(){
        User user = UserThreadLocal.get();
        String username = user.getId().toString();
        String password = DigestUtils.md5Hex(user.getId() + "_xfjy");

        // 用环信用户名密码换取用户级 token（IM SDK 5.x 仅支持 token 登录）
        // 环信用户未注册或服务不可用时返回 null，前端 SDK 登录失败会静默降级
        String token = huanXinApi.getUserToken(username, password);

        HuanXinUser huanXinUser = new HuanXinUser();
        huanXinUser.setUsername(username);
        huanXinUser.setToken(token);

        return ResponseEntity.ok(huanXinUser);
    }

}
