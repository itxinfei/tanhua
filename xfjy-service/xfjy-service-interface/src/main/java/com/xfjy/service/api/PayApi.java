package com.xfjy.service.api;

import com.xfjy.service.pojo.PayOrder;
import com.xfjy.service.pojo.UserVip;
import com.xfjy.service.pojo.VipPackage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 支付服务契约（@HttpExchange 由 server 编排层代理调用）
 */
@HttpExchange("/api/pay")
public interface PayApi {

    /**
     * 查询上架的 VIP 套餐列表（按 sort 升序）
     */
    @GetExchange("/packages")
    @GetMapping("/packages")
    List<VipPackage> queryPackages();

    /**
     * 创建支付订单
     *
     * @param userId    下单用户 id
     * @param packageId 套餐 id
     * @return 订单信息（含订单号与过期时间），失败返回 null
     */
    @PostExchange("/createOrder")
    @PostMapping("/createOrder")
    PayOrder createOrder(@RequestParam("userId") Long userId,
                         @RequestParam("packageId") Long packageId);

    /**
     * 模拟支付确认（沙箱回调：订单 PENDING → PAID → 开通 VIP）
     * 生产环境由真实支付网关异步回调触发，替换本方法实现即可。
     *
     * @param orderNo 订单号
     * @param userId  操作用户 id（校验订单归属）
     * @return 是否成功
     */
    @PostExchange("/confirmPay")
    @PostMapping("/confirmPay")
    Boolean confirmPay(@RequestParam("orderNo") String orderNo,
                       @RequestParam("userId") Long userId);

    /**
     * 查询用户 VIP 状态
     *
     * @param userId 用户 id
     * @return VIP 记录（非会员返回 level=0 的默认记录）
     */
    @GetExchange("/vipStatus")
    @GetMapping("/vipStatus")
    UserVip queryVipStatus(@RequestParam("userId") Long userId);

    /**
     * 根据订单号查询订单详情
     */
    @GetExchange("/order")
    @GetMapping("/order")
    PayOrder queryOrder(@RequestParam("orderNo") String orderNo);
}