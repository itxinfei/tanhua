package com.xfjy.server.controller;

import com.xfjy.server.service.AdminKefuService;
import com.xfjy.server.service.KefuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台客服管理接口：意见反馈处理 + FAQ 维护
 */
@RestController
@RequestMapping("admin/kefu")
public class AdminKefuController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminKefuController.class);

    @Autowired
    private AdminKefuService adminKefuService;

    @Autowired
    private KefuService kefuService;

    // ==================== 客服工作台 ====================

    /**
     * GET /admin/kefu/agent/credential — 获取客服环信登录凭证
     * 前端工作台用 username+token 登录环信 SDK，接收/回复用户消息
     */
    @GetMapping("agent/credential")
    public ResponseEntity getAgentCredential() {
        try {
            return ResponseEntity.ok(this.kefuService.getAgentCredential());
        } catch (Exception e) {
            LOGGER.error("获取客服凭证失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "获取客服凭证失败"));
        }
    }

    /**
     * POST /admin/kefu/users/info — 批量查询用户基础信息
     * body: { userIds: [1, 2, 3] }
     * 工作台会话列表用 conversationId（环信 username = userId）查昵称/头像
     */
    @PostMapping("users/info")
    public ResponseEntity queryUsersInfo(@RequestBody Map<String, Object> body) {
        try {
            Object raw = body.get("userIds");
            if (raw == null) {
                return ResponseEntity.ok(List.of());
            }
            List<Long> userIds = ((List<?>) raw).stream()
                    .map(item -> Long.valueOf(String.valueOf(item)))
                    .collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(this.kefuService.queryUsersInfo(userIds));
        } catch (Exception e) {
            LOGGER.error("批量查询用户信息失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "批量查询用户信息失败"));
        }
    }

    // ==================== 意见反馈 ====================

    /**
     * GET /admin/kefu/feedback — 分页查询反馈列表
     */
    @GetMapping("feedback")
    public ResponseEntity queryFeedbackPage(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                             @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                             @RequestParam(value = "status", required = false) Integer status) {
        try {
            return ResponseEntity.ok(this.adminKefuService.queryFeedbackPage(page, pageSize, status));
        } catch (Exception e) {
            LOGGER.error("查询反馈列表失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "查询反馈列表失败"));
        }
    }

    /**
     * PUT /admin/kefu/feedback/{id}/reply — 回复反馈
     * body: { reply }
     */
    @PutMapping("feedback/{id}/reply")
    public ResponseEntity replyFeedback(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        try {
            String reply = body.get("reply");
            Boolean success = this.adminKefuService.replyFeedback(id, reply);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "回复成功"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "回复失败，记录不存在或回复内容为空"));
        } catch (Exception e) {
            LOGGER.error("回复反馈失败, id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("message", "回复反馈失败"));
        }
    }

    /**
     * PUT /admin/kefu/feedback/{id}/close — 关闭反馈
     */
    @PutMapping("feedback/{id}/close")
    public ResponseEntity closeFeedback(@PathVariable("id") Long id) {
        try {
            Boolean success = this.adminKefuService.closeFeedback(id);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "已关闭"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "关闭失败，记录不存在"));
        } catch (Exception e) {
            LOGGER.error("关闭反馈失败, id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("message", "关闭反馈失败"));
        }
    }

    // ==================== FAQ ====================

    /**
     * GET /admin/kefu/faq — 分页查询 FAQ
     */
    @GetMapping("faq")
    public ResponseEntity queryFaqPage(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                        @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                        @RequestParam(value = "category", required = false) String category,
                                        @RequestParam(value = "status", required = false) Integer status) {
        try {
            return ResponseEntity.ok(this.adminKefuService.queryFaqPage(page, pageSize, category, status));
        } catch (Exception e) {
            LOGGER.error("查询FAQ列表失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "查询FAQ列表失败"));
        }
    }

    /**
     * POST /admin/kefu/faq — 新增 FAQ
     * body: { category, question, answer, sort }
     */
    @PostMapping("faq")
    public ResponseEntity createFaq(@RequestBody Map<String, Object> body) {
        try {
            String category = body.get("category") == null ? null : String.valueOf(body.get("category"));
            String question = body.get("question") == null ? null : String.valueOf(body.get("question"));
            String answer = body.get("answer") == null ? null : String.valueOf(body.get("answer"));
            Integer sort = body.get("sort") == null ? null : Integer.valueOf(String.valueOf(body.get("sort")));
            Boolean success = this.adminKefuService.createFaq(category, question, answer, sort);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "新增成功"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "新增失败，问题重复或内容不完整"));
        } catch (Exception e) {
            LOGGER.error("新增FAQ失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "新增FAQ失败"));
        }
    }

    /**
     * PUT /admin/kefu/faq/{id} — 编辑 FAQ
     */
    @PutMapping("faq/{id}")
    public ResponseEntity updateFaq(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        try {
            Boolean success = this.adminKefuService.updateFaq(id, body);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "编辑成功"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "编辑失败，记录不存在或问题重复"));
        } catch (Exception e) {
            LOGGER.error("编辑FAQ失败, id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("message", "编辑FAQ失败"));
        }
    }

    /**
     * PUT /admin/kefu/faq/{id}/status — FAQ 启用/停用
     * body: { status }
     */
    @PutMapping("faq/{id}/status")
    public ResponseEntity toggleFaq(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        try {
            Integer status = body.get("status") == null ? null : Integer.valueOf(body.get("status"));
            Boolean success = this.adminKefuService.toggleFaq(id, status);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "操作成功"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "操作失败，记录不存在"));
        } catch (Exception e) {
            LOGGER.error("启停FAQ失败, id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("message", "启停FAQ失败"));
        }
    }

    /**
     * DELETE /admin/kefu/faq/{id} — 删除 FAQ
     */
    @DeleteMapping("faq/{id}")
    public ResponseEntity deleteFaq(@PathVariable("id") Long id) {
        try {
            Boolean success = this.adminKefuService.deleteFaq(id);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "删除成功"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "删除失败，记录不存在"));
        } catch (Exception e) {
            LOGGER.error("删除FAQ失败, id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("message", "删除FAQ失败"));
        }
    }
}