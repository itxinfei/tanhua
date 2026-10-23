package com.xfjy.server.controller;

import com.xfjy.server.service.AdminReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后台举报处理Controller
 */
@RestController
@RequestMapping("admin/reports")
public class AdminReportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminReportController.class);

    @Autowired
    private AdminReportService adminReportService;

    /**
     * 分页查询举报列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> queryReportPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType) {
        try {
            return ResponseEntity.ok(this.adminReportService.queryReportPage(page, pageSize, status, targetType));
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 查询举报详情
     */
    @GetMapping("{id}")
    public ResponseEntity<Map<String, Object>> queryReportDetail(@PathVariable("id") Long id) {
        try {
            Map<String, Object> result = this.adminReportService.queryReportDetail(id);
            if (result == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 处理举报
     */
    @PutMapping("{id}/handle")
    public ResponseEntity<Void> handleReport(@PathVariable("id") Long id,
                                              @RequestBody Map<String, String> param) {
        try {
            String result = param.get("result");
            String note = param.get("note");
            Boolean success = this.adminReportService.handleReport(id, result, note);
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 批量处理举报
     */
    @PutMapping("batch-handle")
    public ResponseEntity<Integer> batchHandle(@RequestBody Map<String, Object> param) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) param.get("ids");
            String result = (String) param.get("result");
            String note = (String) param.get("note");
            int count = 0;
            for (Long id : ids) {
                if (this.adminReportService.handleReport(id, result, note)) {
                    count++;
                }
            }
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}