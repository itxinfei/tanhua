package com.xfjy.service.api;

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

    /**
     * 注册环信用户（IM 账号创建）
     *
     * 幂等：用户已存在时返回 true，调用方无需区分是否首次注册
     *
     * @param username 环信用户ID
     * @param password 环信用户密码
     * @return true=注册成功或已存在；false=注册失败
     */
    @PostExchange("/user-register")
    @PostMapping("/user-register")
    boolean registerUser(@RequestParam("username") String username,
                         @RequestParam("password") String password);

    /**
     * 获取环信用户级 token（供 IM SDK 5.x 登录使用）
     *
     * @param username 环信用户ID
     * @param password 环信用户密码
     * @return 用户级 access_token；获取失败返回 null
     */
    @PostExchange("/user-token")
    @PostMapping("/user-token")
    String getUserToken(@RequestParam("username") String username,
                        @RequestParam("password") String password);
}