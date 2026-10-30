package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.service.AdminReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后台内容审核Controller
 */
@RestController
@RequireRole({"ADMIN", "OPERATOR"})
@RequestMapping("admin/reviews")
public class AdminReviewController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminReviewController.class);

    @Autowired
    private AdminReviewService adminReviewService;

    /**
     * 分页查询审核列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> queryReviewPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        try {
            return ResponseEntity.ok(this.adminReviewService.queryReviewPage(page, pageSize, type, status));
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 通过审核
     */
    @PutMapping("{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable("id") Long id) {
        try {
            Boolean success = this.adminReviewService.approve(id);
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 驳回审核
     */
    @PutMapping("{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable("id") Long id,
                                        @RequestBody Map<String, String> param) {
        try {
            Boolean success = this.adminReviewService.reject(id, param.get("reason"));
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 批量通过
     */
    @PutMapping("batch-approve")
    public ResponseEntity<Integer> batchApprove(@RequestBody Map<String, List<Long>> param) {
        try {
            List<Long> ids = param.get("ids");
            Integer count = this.adminReviewService.batchApprove(ids);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 批量驳回
     */
    @PutMapping("batch-reject")
    public ResponseEntity<Integer> batchReject(@RequestBody Map<String, Object> param) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) param.get("ids");
            String reason = (String) param.get("reason");
            Integer count = this.adminReviewService.batchReject(ids, reason);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}