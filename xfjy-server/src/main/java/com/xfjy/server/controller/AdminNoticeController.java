package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.pojo.Notice;
import com.xfjy.server.service.AdminNoticeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台公告管理Controller
 */
@RestController
@RequireRole("ADMIN")
@RequestMapping("admin/notices")
public class AdminNoticeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminNoticeController.class);

    @Autowired
    private AdminNoticeService adminNoticeService;

    /**
     * 分页查询公告
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> queryNoticePage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        try {
            return ResponseEntity.ok(this.adminNoticeService.queryNoticePage(page, pageSize, title, type, status));
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 查询公告详情
     */
    @GetMapping("{id}")
    public ResponseEntity<Notice> queryNoticeDetail(@PathVariable("id") Long id) {
        try {
            Notice notice = this.adminNoticeService.queryNoticeDetail(id);
            if (notice == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(notice);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 新增公告
     */
    @PostMapping
    public ResponseEntity<Notice> createNotice(@RequestBody Map<String, String> param) {
        try {
            Notice notice = this.adminNoticeService.createNotice(
                    param.get("title"), param.get("content"), param.get("type"));
            return ResponseEntity.ok(notice);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 更新公告
     */
    @PutMapping("{id}")
    public ResponseEntity<Void> updateNotice(@PathVariable("id") Long id,
                                              @RequestBody Map<String, String> param) {
        try {
            Boolean success = this.adminNoticeService.updateNotice(
                    id, param.get("title"), param.get("content"), param.get("type"));
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 发布公告
     */
    @PutMapping("{id}/publish")
    public ResponseEntity<Void> publishNotice(@PathVariable("id") Long id) {
        try {
            Boolean success = this.adminNoticeService.publishNotice(id);
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 下线公告
     */
    @PutMapping("{id}/offline")
    public ResponseEntity<Void> offlineNotice(@PathVariable("id") Long id) {
        try {
            Boolean success = this.adminNoticeService.offlineNotice(id);
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除公告
     */
    @DeleteMapping("{id}")
    public ResponseEntity<Void> deleteNotice(@PathVariable("id") Long id) {
        try {
            Boolean success = this.adminNoticeService.deleteNotice(id);
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}