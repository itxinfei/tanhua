package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.service.AdminCommentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后台评论管理Controller
 */
@RestController
@RequireRole("ADMIN")
@RequestMapping("admin/comments")
public class AdminCommentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminCommentController.class);

    @Autowired
    private AdminCommentService adminCommentService;

    /**
     * 分页查询评论列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> queryCommentPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId) {
        try {
            Map<String, Object> result = this.adminCommentService.queryCommentPage(page, pageSize, keyword, userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除评论
     */
    @DeleteMapping("{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable("commentId") String commentId) {
        try {
            Boolean success = this.adminCommentService.deleteComment(commentId);
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 批量删除评论
     */
    @DeleteMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchDeleteComment(@RequestBody List<String> commentIds) {
        try {
            Map<String, Object> result = this.adminCommentService.batchDeleteComment(commentIds);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}