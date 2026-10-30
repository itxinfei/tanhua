package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.service.AdminSensitiveWordService;
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

import java.util.Map;

/**
 * 后台敏感词管理接口
 *
 * <p>词库变更后由 Service 层自动重建 DFA 检测树，线上检测实时生效。</p>
 */
@RestController
@RequireRole("ADMIN")
@RequestMapping("admin/sensitive-words")
public class AdminSensitiveWordController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminSensitiveWordController.class);

    @Autowired
    private AdminSensitiveWordService adminSensitiveWordService;

    /**
     * GET /admin/sensitive-words — 分页查询
     */
    @GetMapping
    public ResponseEntity queryWordPage(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                        @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                        @RequestParam(value = "word", required = false) String word,
                                        @RequestParam(value = "category", required = false) String category,
                                        @RequestParam(value = "level", required = false) Integer level,
                                        @RequestParam(value = "status", required = false) Integer status) {
        try {
            return ResponseEntity.ok(this.adminSensitiveWordService.queryWordPage(
                    page, pageSize, word, category, level, status));
        } catch (Exception e) {
            LOGGER.error("查询敏感词列表失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "查询敏感词列表失败"));
        }
    }

    /**
     * POST /admin/sensitive-words — 新增敏感词
     */
    @PostMapping
    public ResponseEntity createWord(@RequestBody Map<String, String> body) {
        try {
            String word = body.get("word");
            String category = body.get("category");
            Integer level = body.get("level") == null ? null : Integer.valueOf(body.get("level"));
            Boolean success = this.adminSensitiveWordService.createWord(word, category, level);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "新增成功"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "新增失败，敏感词已存在或参数不完整"));
        } catch (Exception e) {
            LOGGER.error("新增敏感词失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "新增敏感词失败"));
        }
    }

    /**
     * PUT /admin/sensitive-words/{id} — 编辑敏感词
     */
    @PutMapping("{id}")
    public ResponseEntity updateWord(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        try {
            String word = body.get("word");
            String category = body.get("category");
            Integer level = body.get("level") == null ? null : Integer.valueOf(body.get("level"));
            Boolean success = this.adminSensitiveWordService.updateWord(id, word, category, level);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "编辑成功"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "编辑失败，敏感词已存在或记录不存在"));
        } catch (Exception e) {
            LOGGER.error("编辑敏感词失败, id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("message", "编辑敏感词失败"));
        }
    }

    /**
     * PUT /admin/sensitive-words/{id}/status — 启用/停用
     */
    @PutMapping("{id}/status")
    public ResponseEntity toggleWord(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        try {
            Integer status = body.get("status") == null ? null : Integer.valueOf(body.get("status"));
            Boolean success = this.adminSensitiveWordService.toggleWord(id, status);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "操作成功"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "操作失败，记录不存在"));
        } catch (Exception e) {
            LOGGER.error("启停敏感词失败, id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("message", "启停敏感词失败"));
        }
    }

    /**
     * DELETE /admin/sensitive-words/{id} — 删除敏感词
     */
    @DeleteMapping("{id}")
    public ResponseEntity deleteWord(@PathVariable("id") Long id) {
        try {
            Boolean success = this.adminSensitiveWordService.deleteWord(id);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "删除成功"));
            }
            return ResponseEntity.status(400).body(Map.of("message", "删除失败，记录不存在"));
        } catch (Exception e) {
            LOGGER.error("删除敏感词失败, id={}", id, e);
            return ResponseEntity.status(500).body(Map.of("message", "删除敏感词失败"));
        }
    }

    /**
     * PUT /admin/sensitive-words/refresh — 手动刷新 DFA 词库
     */
    @PutMapping("refresh")
    public ResponseEntity refresh() {
        try {
            this.adminSensitiveWordService.refresh();
            return ResponseEntity.ok(Map.of("message", "词库刷新成功"));
        } catch (Exception e) {
            LOGGER.error("刷新敏感词库失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "刷新词库失败"));
        }
    }
}