package com.xfjy.server.service;

import com.xfjy.service.api.PayApi;
import com.xfjy.service.pojo.PayOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台支付订单管理服务（只读查询，通过 PayApi 代理到 pay-service）
 *
 * <p>原有直接持有的 PayOrderMapper 已移除——只读查询改为 @HttpExchange 委托 pay-service，
 * 订单状态流转仍由 pay-service 业务链路驱动。</p>
 */
@Service
public class AdminPayOrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminPayOrderService.class);

    private static final int EXPORT_MAX_SIZE = 1000;

    @Autowired
    private PayApi payApi;

    /**
     * 分页查询支付订单
     *
     * @param page     页码
     * @param pageSize 页大小
     * @param orderNo  订单号（模糊匹配）
     * @param status   订单状态：PENDING/PAID/EXPIRED/CANCELLED
     * @param userId   下单用户ID（精确匹配）
     * @param startDate 开始日期（可选，yyyy-MM-dd）
     * @param endDate   结束日期（可选，yyyy-MM-dd）
     */
    public Map<String, Object> queryOrderPage(Integer page, Integer pageSize,
                                               String orderNo, String status, Long userId,
                                               String startDate, String endDate) {
        // 注意：dateRange 过滤暂不在 PayApi 契约中，前端可通过 status + userId 精确筛选
        return this.payApi.queryOrderPage(page, pageSize, orderNo, status, userId);
    }

    /**
     * 汇总统计
     *
     * @param status 可选状态过滤
     */
    public Map<String, Object> queryOrderSummary(String status, String startDate, String endDate) {
        return this.payApi.queryOrderSummary(status, startDate, endDate);
    }

    /**
     * 导出订单列表（返回全部匹配数据，不分页，上限 EXPORT_MAX_SIZE）
     */
    public List<PayOrder> exportOrders(String orderNo, String status, Long userId,
                                       String startDate, String endDate) {
        Map<String, Object> result = this.payApi.queryOrderPage(1, EXPORT_MAX_SIZE,
                orderNo, status, userId);
        if (result == null) {
            return new java.util.ArrayList<>();
        }
        Object listObj = result.get("list");
        if (listObj instanceof List) {
            return (List<PayOrder>) listObj;
        }
        return new java.util.ArrayList<>();
    }
}
