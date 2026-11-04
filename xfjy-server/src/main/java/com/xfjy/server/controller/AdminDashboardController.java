package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.service.AdminDashboardService;
import com.xfjy.server.service.AdminDashboardStatsService;
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
@RequireRole({"SUPER_ADMIN", "ADMIN", "OPERATOR"})
@RequestMapping("admin/dashboard")
public class AdminDashboardController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminDashboardController.class);

    @Autowired
    private AdminDashboardService adminDashboardService;

    @Autowired
    private AdminDashboardStatsService adminDashboardStatsService;

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

    /**
     * 支付订单趋势（近N天已支付订单数）
     */
    @GetMapping("pay-trend")
    public ResponseEntity<Map<String, Object>> payTrend(@RequestParam(defaultValue = "7") Integer days) {
        try {
            return ResponseEntity.ok(this.adminDashboardService.payTrend(days));
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 支付方式分布
     */
    @GetMapping("pay-type")
    public ResponseEntity<Map<String, Object>> payType() {
        try {
            return ResponseEntity.ok(this.adminDashboardService.payTypeDistribution());
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 实时统计：今日消息数、动态发布数、视频发布数
     */
    @GetMapping("realtime-stats")
    public ResponseEntity<Map<String, Object>> realtimeStats() {
        try {
            return ResponseEntity.ok(this.adminDashboardService.realtimeStats());
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 在线用户统计：当前在线数、今日峰值
     */
    @GetMapping("online-stats")
    public ResponseEntity<Map<String, Object>> onlineStats() {
        try {
            return ResponseEntity.ok(this.adminDashboardService.onlineStats());
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 热门内容 TOP5：动态 + 视频
     */
    @GetMapping("hot-content")
    public ResponseEntity<Map<String, Object>> hotContent() {
        try {
            return ResponseEntity.ok(this.adminDashboardService.hotContent());
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 按维度趋势：动态发布趋势 + 视频发布趋势（近N天）
     */
    @GetMapping("trend-by-dimension")
    public ResponseEntity<Map<String, Object>> trendByDimension(@RequestParam(defaultValue = "7") Integer days) {
        try {
            return ResponseEntity.ok(this.adminDashboardService.trendByDimension(days));
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 实时在线概览：当前在线数、今日峰值、今日活跃 DAU（看板 30s 快轮询使用）
     */
    @GetMapping("online-overview")
    public Map<String, Object> onlineOverview() {
        return this.adminDashboardStatsService.online();
    }

    /**
     * 活跃与留存：近 N 天 DAU 序列、今日活跃、次日/3/7/30 日留存
     */
    @GetMapping("activity")
    public Map<String, Object> activity(@RequestParam(defaultValue = "30") Integer days) {
        return this.adminDashboardStatsService.activity(days);
    }

    /**
     * 匹配统计：今日漏斗（滑动→喜欢→匹配）+ 近 N 天每日序列
     */
    @GetMapping("match-stats")
    public Map<String, Object> matchStats(@RequestParam(defaultValue = "7") Integer days) {
        return this.adminDashboardStatsService.matchStats(days);
    }

    /**
     * 人口属性聚合：性别、年龄段、城市 TOP10
     */
    @GetMapping("demographics")
    public Map<String, Object> demographics() {
        return this.adminDashboardStatsService.demographics();
    }

    /**
     * 收入与付费：收入概览、近 N 天订单/金额趋势、支付方式、付费率、今日 ARPPU（金额单位：分）
     */
    @GetMapping("revenue")
    public Map<String, Object> revenue(@RequestParam(defaultValue = "7") Integer days) {
        return this.adminDashboardStatsService.revenue(days);
    }

    /**
     * 内容安全：审核与举报的存量、今日处理量、近 N 天每日序列与类型分布
     */
    @GetMapping("moderation")
    public Map<String, Object> moderation(@RequestParam(defaultValue = "7") Integer days) {
        return this.adminDashboardStatsService.moderation(days);
    }
}