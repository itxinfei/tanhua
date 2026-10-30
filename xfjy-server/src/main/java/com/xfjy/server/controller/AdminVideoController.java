package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.service.AdminVideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后台视频内容管理Controller
 */
@RestController
@RequireRole("ADMIN")
@RequestMapping("admin/videos")
public class AdminVideoController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminVideoController.class);

    @Autowired
    private AdminVideoService adminVideoService;

    /**
     * 分页查询视频列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> queryVideoPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId) {
        try {
            Map<String, Object> result = this.adminVideoService.queryVideoPage(page, pageSize, keyword, userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除（下架）视频
     */
    @DeleteMapping("{videoId}")
    public ResponseEntity<Void> deleteVideo(@PathVariable("videoId") String videoId) {
        try {
            Boolean success = this.adminVideoService.deleteVideo(videoId);
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 批量删除视频
     */
    @DeleteMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchDeleteVideo(@RequestBody List<String> videoIds) {
        try {
            Map<String, Object> result = this.adminVideoService.batchDeleteVideo(videoIds);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}