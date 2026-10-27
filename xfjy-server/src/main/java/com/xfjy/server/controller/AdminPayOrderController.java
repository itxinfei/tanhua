package com.xfjy.server.controller;

import com.xfjy.server.service.AdminPayOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台支付订单管理Controller（只读查询）
 */
@RestController
@RequestMapping("admin/pay-orders")
public class AdminPayOrderController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminPayOrderController.class);

    @Autowired
    private AdminPayOrderService adminPayOrderService;

    /**
     * 分页查询支付订单
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> queryOrderPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId) {
        try {
            Map<String, Object> result = this.adminPayOrderService.queryOrderPage(page, pageSize, orderNo, status, userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}