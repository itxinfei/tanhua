package com.xfjy.dubbo.server.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 环信 IM 集成契约（xfjy-message-service，18086）
 *
 * 由原 xfjy-sso HuanXinController 迁出并契约化：
 * - 好友关系注册到环信（添加联系人时调用）
 * - 发送环信透传消息（回复陌生人问题等场景调用）
 */
@HttpExchange("/api/huanxin")
public interface HuanXinApi {

    /**
     * 好友关系注册到环信
     *
     * @param userId   当前用户ID
     * @param friendId 好友用户ID
     */
    @PostExchange("/contacts/{userId}/{friendId}")
    @PostMapping("/contacts/{userId}/{friendId}")
    void registerContact(@PathVariable("userId") Long userId,
                         @PathVariable("friendId") Long friendId);

    /**
     * 发送环信透传消息
     *
     * @param target 目标环信用户ID（即用户ID字符串）
     * @param msg    消息内容 JSON
     */
    @PostExchange("/messages")
    @PostMapping("/messages")
    void sendMessage(@RequestParam("target") String target,
                     @RequestParam("msg") String msg);
}