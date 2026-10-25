package com.xfjy.service.api;

import com.xfjy.pay.service.PayService;
import com.xfjy.service.pojo.PayOrder;
import com.xfjy.service.pojo.UserVip;
import com.xfjy.service.pojo.VipPackage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 支付服务契约实现（由 server 编排层通过 @HttpExchange 代理调用）
 */
@RestController
@RequestMapping("/api/pay")
public class PayApiImpl implements PayApi {

    @Autowired
    private PayService payService;

    @Override
    @GetMapping("/packages")
    public List<VipPackage> queryPackages() {
        return this.payService.queryPackages();
    }

    @Override
    @PostMapping("/createOrder")
    public PayOrder createOrder(@RequestParam("userId") Long userId,
                                @RequestParam("packageId") Long packageId) {
        return this.payService.createOrder(userId, packageId);
    }

    @Override
    @PostMapping("/confirmPay")
    public Boolean confirmPay(@RequestParam("orderNo") String orderNo,
                              @RequestParam("userId") Long userId) {
        return this.payService.confirmPay(orderNo, userId);
    }

    @Override
    @GetMapping("/vipStatus")
    public UserVip queryVipStatus(@RequestParam("userId") Long userId) {
        return this.payService.queryVipStatus(userId);
    }

    @Override
    @GetMapping("/order")
    public PayOrder queryOrder(@RequestParam("orderNo") String orderNo) {
        return this.payService.queryOrder(orderNo);
    }
}