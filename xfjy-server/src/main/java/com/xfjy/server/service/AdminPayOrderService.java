package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.server.mapper.PayOrderMapper;
import com.xfjy.service.pojo.PayOrder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 后台支付订单管理服务（只读查询，订单状态流转由 pay-service 业务链路驱动）
 */
@Service
public class AdminPayOrderService {

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
     */
    public Map<String, Object> queryOrderPage(Integer page, Integer pageSize,
                                               String orderNo, String status, Long userId) {
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
        wrapper.orderByDesc("created");

        IPage<PayOrder> orderPage = this.payOrderMapper.selectPage(new Page<>(page, pageSize), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", orderPage.getTotal());
        result.put("list", orderPage.getRecords());
        return result;
    }
}