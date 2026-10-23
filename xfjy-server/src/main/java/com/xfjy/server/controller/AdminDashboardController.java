package com.xfjy.server.controller;

import com.xfjy.server.service.AdminDashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台数据看板Controller
 */
@RestController
@RequestMapping("admin/dashboard")
public class AdminDashboardController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminDashboardController.class);

    @Autowired
    private AdminDashboardService adminDashboardService;

    /**
     * 看板概览统计
     */
    @GetMapping("overview")
    public ResponseEntity<Map<String, Object>> overview() {
        try {
            return ResponseEntity.ok(this.adminDashboardService.overview());
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 趋势数据（近N天）
     */
    @GetMapping("trend")
    public ResponseEntity<Map<String, Object>> trend(@RequestParam(defaultValue = "7") Integer days) {
        try {
            return ResponseEntity.ok(this.adminDashboardService.trend(days));
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 性别比例
     */
    @GetMapping("gender-ratio")
    public ResponseEntity<Map<String, Object>> genderRatio() {
        try {
            return ResponseEntity.ok(this.adminDashboardService.genderRatio());
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 年龄分布
     */
    @GetMapping("age-distribution")
    public ResponseEntity<Map<String, Object>> ageDistribution() {
        try {
            return ResponseEntity.ok(this.adminDashboardService.ageDistribution());
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}