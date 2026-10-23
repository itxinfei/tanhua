package com.xfjy.service.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 环信 IM 集成契约实现（xfjy-message-service，18086）
 *
 * 由原 xfjy-sso HuanXinController 迁出：
 * - 好友关系注册到环信（添加联系人时由 xfjy-server IMService 调用）
 * - 发送环信透传消息（回复陌生人问题等场景由 xfjy-server TodayBestService 调用）
 *
 * TODO: 配置真实环信账号后，启用 getAppToken / 环信 REST API 调用逻辑
 */
@Slf4j
@RestController
@RequestMapping("/api/huanxin")
public class HuanXinApiImpl implements HuanXinApi {

    @Value("${huanxin.appkey:}")
    private String appkey;

    @Value("${huanxin.client-id:}")
    private String clientId;

    @Value("${huanxin.client-secret:}")
    private String clientSecret;

    @Value("${huanxin.rest-host:https://a1.easemob.com}")
    private String restHost;

    /**
     * 好友关系注册到环信
     */
    @Override
    @PostMapping("/contacts/{userId}/{friendId}")
    public void registerContact(@PathVariable("userId") Long userId,
                                @PathVariable("friendId") Long friendId) {
        log.info("[环信] 注册好友关系: {} -> {}", userId, friendId);
        // TODO: 调用环信 REST API
        //   1. getAppToken() 获取应用级 token（client_credentials 模式）
        //   2. POST {restHost}/{org}/{app}/users/{userId}/contacts/users/{friendId}
        //      Header: Authorization: Bearer {token}
    }

    /**
     * 发送环信透传消息
     */
    @Override
    @PostMapping("/messages")
    public void sendMessage(@RequestParam("target") String target,
                            @RequestParam("msg") String msg) {
        log.info("[环信] 发送消息 -> {}: {}", target, msg);
        // TODO: 调用环信 REST API
        //   1. getAppToken() 获取应用级 token
        //   2. POST {restHost}/{org}/{app}/messages/users
        //      body: { "target": [target], "msg": { "type": "txt", "msg": msg } }
    }

    /**
     * 获取环信应用级 token（client_credentials 模式）
     *
     * TODO: 实现环信 REST 调用
     *   POST {restHost}/{org}/{app}/token
     *   body: { "grant_type": "client_credentials", "client_id": clientId, "client_secret": clientSecret }
     *   返回 access_token 有效期较长，建议缓存
     */
    private String getAppToken() {
        // TODO: 待真实环信账号配置后实现
        return null;
    }
}