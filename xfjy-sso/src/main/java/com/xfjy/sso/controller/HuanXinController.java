package com.xfjy.sso.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 环信 IM 辅助接口（xfjy-server 调用契约）
 *
 * TODO: 阶段三 message-service 拆分时迁出本模块；当前保持契约返回 200，
 *       环信 REST 对接（好友注册/消息下发）在 message-service 中实现
 */
@Slf4j
@RestController
public class HuanXinController {

    /**
     * 好友关系注册到环信
     */
    @PostMapping("/user/huanxin/contacts/{userId}/{friendId}")
    public ResponseEntity<Void> registerContact(@PathVariable Long userId, @PathVariable Long friendId) {
        log.info("[环信] 注册好友关系: {} -> {}", userId, friendId);
        // TODO: 调用环信 REST API POST /users/{owner}/contacts/users/{friend}
        return ResponseEntity.ok().build();
    }

    /**
     * 发送环信透传消息
     *
     * @param target 目标环信用户ID（即用户ID字符串）
     * @param msg    消息内容 JSON
     */
    @PostMapping("/user/huanxin/messages")
    public ResponseEntity<Void> sendMessage(@RequestParam("target") String target,
                                            @RequestParam("msg") String msg) {
        log.info("[环信] 发送消息 -> {}: {}", target, msg);
        // TODO: 调用环信 REST API POST /messages/users
        return ResponseEntity.ok().build();
    }
}
