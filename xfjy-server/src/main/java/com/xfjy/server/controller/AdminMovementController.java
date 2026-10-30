package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.service.AdminMovementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后台动态内容管理Controller
 */
@RestController
@RequireRole("ADMIN")
@RequestMapping("admin/movements")
public class AdminMovementController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminMovementController.class);

    @Autowired
    private AdminMovementService adminMovementService;

    /**
     * 分页查询动态列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> queryMovementPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            Map<String, Object> result = this.adminMovementService.queryMovementPage(page, pageSize, keyword, userId, startDate, endDate);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除（下架）动态
     */
    @DeleteMapping("{publishId}")
    public ResponseEntity<Void> deleteMovement(@PathVariable("publishId") String publishId) {
        try {
            Boolean success = this.adminMovementService.deleteMovement(publishId);
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 批量删除动态
     */
    @DeleteMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchDeleteMovement(@RequestBody List<String> publishIds) {
        try {
            Map<String, Object> result = this.adminMovementService.batchDeleteMovement(publishIds);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}