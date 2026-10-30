package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.server.mapper.PayOrderMapper;
import com.xfjy.service.pojo.PayOrder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台支付订单管理服务（只读查询，订单状态流转由 pay-service 业务链路驱动）
 */
@Service
public class AdminPayOrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminPayOrderService.class);

    @Autowired
    private PayOrderMapper payOrderMapper;

    /**
     * 分页查询支付订单
     *
     * @param page    页码
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
        QueryWrapper<PayOrder> wrapper = buildFilterWrapper(orderNo, status, userId, startDate, endDate);
        wrapper.orderByDesc("created");

        IPage<PayOrder> orderPage = this.payOrderMapper.selectPage(new Page<>(page, pageSize), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", orderPage.getTotal());
        result.put("list", orderPage.getRecords());
        return result;
    }

    /**
     * 汇总统计（全部订单，不受分页限制）
     *
     * @param status 可选状态过滤
     */
    public Map<String, Object> queryOrderSummary(String status, String startDate, String endDate) {
        QueryWrapper<PayOrder> wrapper = buildFilterWrapper(null, status, null, startDate, endDate);

        long total = this.payOrderMapper.selectCount(wrapper);
        long paidCount = this.payOrderMapper.selectCount(
                buildFilterWrapper(null, "PAID", null, startDate, endDate));
        long pendingCount = this.payOrderMapper.selectCount(
                buildFilterWrapper(null, "PENDING", null, startDate, endDate));

        // 已支付总额（分）
        QueryWrapper<PayOrder> paidWrapper = buildFilterWrapper(null, "PAID", null, startDate, endDate);
        paidWrapper.select("COALESCE(SUM(amount), 0) AS total_amount");
        Map<String, Object> paidRow = this.payOrderMapper.selectMaps(paidWrapper).get(0);
        long totalAmount = paidRow != null && paidRow.get("total_amount") != null
                ? ((Number) paidRow.get("total_amount")).longValue() : 0L;

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("paidCount", paidCount);
        result.put("pendingCount", pendingCount);
        result.put("totalAmount", totalAmount);
        return result;
    }

    /**
     * 导出订单列表（返回全部匹配数据，不分页）
     */
    public List<PayOrder> exportOrders(String orderNo, String status, Long userId,
                                       String startDate, String endDate) {
        QueryWrapper<PayOrder> wrapper = buildFilterWrapper(orderNo, status, userId, startDate, endDate);
        wrapper.orderByDesc("created");
        return this.payOrderMapper.selectList(wrapper);
    }

    /**
     * 构建过滤条件（分页/汇总/导出共用）
     */
    private QueryWrapper<PayOrder> buildFilterWrapper(String orderNo, String status, Long userId,
                                                      String startDate, String endDate) {
        QueryWrapper<PayOrder> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(orderNo)) {
            wrapper.like("order_no", orderNo);
        }
        if (StringUtils.isNotBlank(status)) {
            wrapper.eq("status", status);
        }
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (StringUtils.isNotBlank(startDate)) {
            wrapper.ge("created", java.sql.Date.valueOf(startDate).getTime());
        }
        if (StringUtils.isNotBlank(endDate)) {
            wrapper.le("created", java.sql.Date.valueOf(endDate).getTime() + 86399999L);
        }
        return wrapper;
    }
}