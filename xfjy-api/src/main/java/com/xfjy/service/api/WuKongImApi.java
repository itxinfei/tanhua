package com.xfjy.service.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;
import java.util.Map;

/**
 * WuKongIM 开源 IM 集成契约（xfjy-message-service，18086）
 *
 * 单聊频道模型：channel_type=1 为「个人频道」，channel_id = 接收方 uid。
 * A 给 B 发消息即 from_uid=A、channel_id=B；频道随首条消息自动建立，
 * 无需也不能加订阅者（个人频道 subscriber_add 服务端 400）。
 *
 * 能力清单：
 * - 好友关系注册（个人频道模型下为 no-op，好友关系以业务库为准）
 * - 发送消息（channel_id=接收方 uid，payload=Base64(JSON)）
 * - 注册/更新用户 token（业务系统自定义 uid+token）
 *
 * WuKongIM REST API 文档：https://wukong.mintlify.app/en/api/introduction.md
 * 鉴权模型：内部系统专用，无需 Token
 */
@HttpExchange("/api/wukongim")
public interface WuKongImApi {

    /**
     * 好友关系注册（个人频道模型下为 no-op）
     *
     * 单聊 channel_id=接收方 uid、channel_type=1，频道随首条消息自动建立并投递，
     * 无需也不能加订阅者；好友关系统一以业务库 MongoDB 为准。保留方法仅为维持调用契约。
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
     * 通过 WuKongIM /channel/messagesync 拉取：login_uid=fromUserId，
     * channel_id=toUserId（对方个人频道），服务端返回该会话双方的完整历史消息。
     *
     * @param fromUserId 登录视角用户ID
     * @param toUserId   对方用户ID（即个人频道 channel_id）
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

    /**
     * 今日消息数统计（后台看板使用）
     *
     * 通过 WuKongIM /message/statistics 获取今日消息总量
     * WuKongIM 不支持该端点时返回 null，前端显示「待接入」
     *
     * @return 今日消息数；获取失败返回 null
     */
    @GetExchange("/message-stats")
    @GetMapping("/message-stats")
    Long getTodayMessageCount();
}