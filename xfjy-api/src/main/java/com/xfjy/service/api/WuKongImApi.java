package com.xfjy.service.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;
import java.util.Map;

/**
 * WuKongIM 开源 IM 集成契约（xfjy-message-service，18086）
 *
 * 方法签名与 {@link HuanXinApi} 完全对齐，供 ImProviderDelegate 按配置委派：
 * - 好友关系注册到 WuKongIM（单聊频道双订阅）
 * - 发送消息（payload=Base64(JSON)）
 * - 注册/更新用户 token（业务系统自定义 uid+token）
 *
 * WuKongIM REST API 文档：https://wukong.mintlify.app/en/api/introduction.md
 * 鉴权模型：内部系统专用，无需 Token（与环信 app token 模型不同）
 */
@HttpExchange("/api/wukongim")
public interface WuKongImApi {

    /**
     * 好友关系注册到 WuKongIM
     *
     * 单聊频道 channel_type=1，channel_id=较小uid_较大uid
     * 双方均加入频道订阅者，保证互相收发消息
     *
     * @param userId   当前用户ID
     * @param friendId 好友用户ID
     */
    @PostExchange("/contacts/{userId}/{friendId}")
    @PostMapping("/contacts/{userId}/{friendId}")
    void registerContact(@PathVariable("userId") Long userId,
                         @PathVariable("friendId") Long friendId);

    /**
     * 发送消息
     *
     * @param target 目标用户ID字符串
     * @param msg    消息内容 JSON（实现内 Base64 编码后作为 payload）
     */
    @PostExchange("/messages")
    @PostMapping("/messages")
    void sendMessage(@RequestParam("target") String target,
                     @RequestParam("msg") String msg);

    /**
     * 注册 WuKongIM 用户（更新 uid+token 到 IM 服务器）
     *
     * 幂等：重复注册同一 uid 仅覆盖 token，视为成功
     *
     * @param username 用户ID（作为 WuKongIM uid）
     * @param password 用户密码（作为 WuKongIM token）
     * @return true=注册成功；false=注册失败
     */
    @PostExchange("/user-register")
    @PostMapping("/user-register")
    boolean registerUser(@RequestParam("username") String username,
                         @RequestParam("password") String password);

    /**
     * 获取用户级 token（供 WuKongIM SDK 连接鉴权使用）
     *
     * WuKongIM 模型：业务系统自定义 token，调 /user/token 注册后返回
     *
     * @param username 用户ID
     * @param password 用户密码（作为 token 注册到 IM 服务器）
     * @return 用户 token；失败返回 null
     */
    @PostExchange("/user-token")
    @PostMapping("/user-token")
    String getUserToken(@RequestParam("username") String username,
                        @RequestParam("password") String password);

    /**
     * 获取用户会话列表（后台用户详情弹窗「聊天记录」tab 使用）
     *
     * 通过 WuKongIM /conversation/sync 拉取会话，msg_count=1 取最后消息组装摘要
     *
     * @param userId 用户ID
     * @param limit  会话数量上限
     * @return 会话列表，每项含 contactId / lastMessage / lastTime
     */
    @PostExchange("/chat-sessions")
    @PostMapping("/chat-sessions")
    List<Map<String, Object>> getChatSessions(@RequestParam("userId") String userId,
                                               @RequestParam("limit") Integer limit);

    /**
     * 获取与某用户的聊天记录（后台用户详情弹窗「聊天记录」tab 使用）
     *
     * 通过 WuKongIM /channel/messagesync 按 seq 范围拉取两用户单聊频道历史消息
     *
     * @param fromUserId 发送方用户ID
     * @param toUserId   接收方用户ID
     * @param limit      返回条数上限
     * @param cursor     翻页游标（首次传 null 或空字符串）
     * @return {messages: [{from, content, time, type}], cursor: "..."}
     */
    @PostExchange("/chat-messages")
    @PostMapping("/chat-messages")
    Map<String, Object> getChatMessages(@RequestParam("fromUserId") String fromUserId,
                                         @RequestParam("toUserId") String toUserId,
                                         @RequestParam("limit") Integer limit,
                                         @RequestParam(value = "cursor", required = false) String cursor);
}