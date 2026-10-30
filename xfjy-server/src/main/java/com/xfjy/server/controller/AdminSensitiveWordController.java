package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.service.AdminSensitiveWordService;
import com.xfjy.server.pojo.SensitiveWord;
import com.xfjy.server.vo.TextDetectResult;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
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

    /**
     * POST /admin/sensitive-words/test — 敏感词命中测试
     */
    @PostMapping("test")
    public ResponseEntity<TextDetectResult> testSensitiveWord(@RequestBody Map<String, String> body) {
        try {
            String text = body.get("text");
            TextDetectResult result = this.adminSensitiveWordService.testSensitiveWord(text);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("敏感词测试失败", e);
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * POST /admin/sensitive-words/batch-import — 批量导入敏感词
     */
    @PostMapping("batch-import")
    public ResponseEntity<Map<String, Object>> batchImportWords(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> words = (List<String>) body.get("words");
            String category = (String) body.get("category");
            Integer level = body.get("level") != null ? Integer.valueOf(body.get("level").toString()) : null;

            Map<String, Object> result = this.adminSensitiveWordService.batchImportWords(words, category, level);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("批量导入敏感词失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "批量导入失败"));
        }
    }

    /**
     * GET /admin/sensitive-words/export — 导出敏感词列表（CSV）
     */
    @GetMapping("export")
    public void exportWords(HttpServletResponse response) throws IOException {
        try {
            List<SensitiveWord> words = this.adminSensitiveWordService.exportWords();

            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=sensitive-words.csv");
            PrintWriter writer = response.getWriter();

            // CSV 头
            writer.println("ID,敏感词,分类,级别,状态,创建时间,更新时间");

            // 数据行
            for (SensitiveWord word : words) {
                String created = word.getCreated() != null ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(word.getCreated()) : "";
                String updated = word.getUpdated() != null ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(word.getUpdated()) : "";
                writer.println(String.join(",",
                    String.valueOf(word.getId()),
                    word.getWord(),
                    word.getCategory(),
                    String.valueOf(word.getLevel()),
                    String.valueOf(word.getStatus()),
                    created,
                    updated
                ));
            }
            writer.flush();
        } catch (Exception e) {
            LOGGER.error("导出敏感词失败", e);
            response.setStatus(500);
            response.getWriter().println("导出失败: " + e.getMessage());
        }
    }
}