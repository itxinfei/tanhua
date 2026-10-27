package com.xfjy.server.controller;

import com.xfjy.server.service.KefuService;
import com.xfjy.server.utils.NoAuthorization;
import com.xfjy.server.utils.UserThreadLocal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 客服接口（app 端）：FAQ 查询 + 意见反馈
 */
@RestController
@RequestMapping("kefu")
public class KefuController {

    private static final Logger LOGGER = LoggerFactory.getLogger(KefuController.class);

    @Autowired
    private KefuService kefuService;

    /**
     * GET /kefu/faq — 查询启用中的 FAQ 列表（免登录）
     */
    @GetMapping("faq")
    @NoAuthorization
    public ResponseEntity queryFaqList() {
        return ResponseEntity.ok(this.kefuService.listEnabledFaq());
    }

    /**
     * POST /kefu/feedback — 提交意见反馈
     * body: { content, contact? }
     */
    @PostMapping("feedback")
    public ResponseEntity submitFeedback(@RequestBody Map<String, String> body) {
        try {
            Long userId = UserThreadLocal.get().getId();
            String content = body.get("content");
            String contact = body.get("contact");
            Boolean success = this.kefuService.submitFeedback(userId, content, contact);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "提交成功，客服将尽快处理"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "提交失败，反馈内容不能为空"));
        } catch (Exception e) {
            LOGGER.error("提交意见反馈失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "提交反馈失败"));
        }
    }

    /**
     * GET /kefu/feedback/my — 查询我的反馈列表
     */
    @GetMapping("feedback/my")
    public ResponseEntity myFeedbackList() {
        try {
            Long userId = UserThreadLocal.get().getId();
            List<?> list = this.kefuService.myFeedbackList(userId);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            LOGGER.error("查询我的反馈列表失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "查询反馈列表失败"));
        }
    }
}