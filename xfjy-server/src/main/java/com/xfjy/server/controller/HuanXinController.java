package com.xfjy.server.controller;

import com.xfjy.server.pojo.User;
import com.xfjy.server.service.SysConfigService;
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
    private final SysConfigService sysConfigService;

    public HuanXinController(HuanXinApi huanXinApi, SysConfigService sysConfigService) {
        this.huanXinApi = huanXinApi;
        this.sysConfigService = sysConfigService;
    }

    @GetMapping("user")
    public ResponseEntity<HuanXinUser> queryUser(){
        User user = UserThreadLocal.get();
        String username = user.getId().toString();
        String password = DigestUtils.md5Hex(user.getId() + "_xfjy");

        // 用环信用户名密码换取用户级 token（IM SDK 5.x 仅支持 token 登录）
        String token = huanXinApi.getUserToken(username, password);

        // 首次使用时环信侧无账号，懒注册后重试换取 token
        if (token == null && huanXinApi.registerUser(username, password)) {
            token = huanXinApi.getUserToken(username, password);
        }

        // token 仍为 null 时（IM 服务不可用等），前端 SDK 登录失败会静默降级
        HuanXinUser huanXinUser = new HuanXinUser();
        huanXinUser.setUsername(username);
        huanXinUser.setToken(token);
        // 前端按 provider 选择对应 IM SDK（wukongim/huanxin）
        huanXinUser.setProvider(sysConfigService.getImProvider());

        return ResponseEntity.ok(huanXinUser);
    }

}
