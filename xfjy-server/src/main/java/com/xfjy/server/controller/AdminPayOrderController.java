package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.service.AdminPayOrderService;
import com.xfjy.service.pojo.PayOrder;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * 后台支付订单管理Controller（只读查询）
 */
@RestController
@RequireRole("ADMIN")
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
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            Map<String, Object> result = this.adminPayOrderService.queryOrderPage(page, pageSize, orderNo, status, userId, startDate, endDate);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 汇总统计
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> queryOrderSummary(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            Map<String, Object> result = this.adminPayOrderService.queryOrderSummary(status, startDate, endDate);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 导出订单列表（CSV）
     */
    @GetMapping("/export")
    public void exportOrders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) throws IOException {
        try {
            List<PayOrder> orders = this.adminPayOrderService.exportOrders(orderNo, status, userId, startDate, endDate);

            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=pay-orders.csv");
            PrintWriter writer = response.getWriter();

            // CSV 头
            writer.println("订单号,用户ID,套餐名称,金额(元),状态,支付方式,创建时间,支付时间");

            // 数据行
            for (PayOrder order : orders) {
                String amount = order.getAmount() != null ? String.format("%.2f", order.getAmount() / 100.0) : "0.00";
                String created = order.getCreated() != null ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(order.getCreated()) : "";
                String paid = order.getPaidTime() != null ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(order.getPaidTime()) : "";
                writer.println(String.join(",",
                    order.getOrderNo(),
                    String.valueOf(order.getUserId()),
                    order.getPackageName(),
                    amount,
                    order.getStatus(),
                    order.getPayType(),
                    created,
                    paid
                ));
            }
            writer.flush();
        } catch (Exception e) {
            LOGGER.error("导出订单失败", e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.getWriter().println("导出失败: " + e.getMessage());
        }
    }
}