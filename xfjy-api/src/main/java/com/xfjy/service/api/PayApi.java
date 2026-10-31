package com.xfjy.service.api;

import com.xfjy.service.pojo.PayOrder;
import com.xfjy.service.pojo.UserVip;
import com.xfjy.service.pojo.ValueAddedService;
import com.xfjy.service.pojo.VipPackage;
import com.xfjy.service.vo.BenefitCheckVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.List;
import java.util.Map;

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
     * 取消订单（用户取消支付）：PENDING → CANCELLED
     *
     * @param orderNo 订单号
     * @param userId  操作用户 id（校验订单归属）
     * @return 是否取消成功
     */
    @PostExchange("/cancelOrder")
    @PostMapping("/cancelOrder")
    Boolean cancelOrder(@RequestParam("orderNo") String orderNo,
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

    /**
     * 查询上架的增值服务权益列表（按 sort 升序）
     */
    @GetExchange("/benefits")
    @GetMapping("/benefits")
    List<ValueAddedService> queryBenefits();

    /**
     * 权益校验：VIP 等级是否达标 + 每日限次权益的剩余次数
     *
     * @param userId      用户 id
     * @param serviceCode 权益码
     * @return 校验结果；权益码不存在返回 null
     */
    @GetExchange("/benefit/check")
    @GetMapping("/benefit/check")
    BenefitCheckVo checkBenefit(@RequestParam("userId") Long userId,
                                @RequestParam("serviceCode") String serviceCode);

    /**
     * 记录权益使用（每日限次权益扣减次数）
     *
     * @return true=扣减成功或权益不限次；false=今日次数已用尽或权益不存在
     */
    @PostExchange("/benefit/usage")
    @PostMapping("/benefit/usage")
    Boolean recordBenefitUsage(@RequestParam("userId") Long userId,
                               @RequestParam("serviceCode") String serviceCode);

    // ==================== 后台管理接口 ====================

    /**
     * 后台只读：支付订单分页查询
     */
    @GetExchange("/admin/orders")
    @GetMapping("/admin/orders")
    Map<String, Object> queryOrderPage(@RequestParam("page") Integer page,
                                       @RequestParam("pageSize") Integer pageSize,
                                       @RequestParam(value = "orderNo", required = false) String orderNo,
                                       @RequestParam(value = "status", required = false) String status,
                                       @RequestParam(value = "userId", required = false) Long userId);

    /**
     * 后台只读：订单汇总统计
     */
    @GetExchange("/admin/orders/summary")
    @GetMapping("/admin/orders/summary")
    Map<String, Object> queryOrderSummary(@RequestParam(value = "status", required = false) String status,
                                          @RequestParam(value = "startDate", required = false) String startDate,
                                          @RequestParam(value = "endDate", required = false) String endDate);

    /**
     * 后台管理：新增 VIP 套餐
     */
    @PostExchange("/admin/packages")
    @PostMapping("/admin/packages")
    Boolean createPackage(@RequestParam("name") String name,
                          @RequestParam("level") Integer level,
                          @RequestParam("price") Long price,
                          @RequestParam(value = "originalPrice", required = false) Long originalPrice,
                          @RequestParam("durationDays") Integer durationDays,
                          @RequestParam(value = "description", required = false) String description,
                          @RequestParam(value = "sort", required = false) Integer sort);

    /**
     * 后台管理：编辑 VIP 套餐
     */
    @PutExchange("/admin/packages/{id}")
    @PutMapping("/admin/packages/{id}")
    Boolean updatePackage(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    /**
     * 后台管理：套餐上架/下架
     */
    @PostExchange("/admin/packages/{id}/status")
    @PostMapping("/admin/packages/{id}/status")
    Boolean togglePackage(@PathVariable("id") Long id, @RequestParam("status") Integer status);

    /**
     * 后台管理：编辑增值权益
     */
    @PutExchange("/admin/benefits/{id}")
    @PutMapping("/admin/benefits/{id}")
    Boolean updateBenefit(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    /**
     * 后台管理：权益启用/停用
     */
    @PostExchange("/admin/benefits/{id}/status")
    @PostMapping("/admin/benefits/{id}/status")
    Boolean toggleBenefit(@PathVariable("id") Long id, @RequestParam("status") Integer status);

    /**
     * 后台分页查询 VIP 套餐
     */
    @GetExchange("/admin/packages/page")
    @GetMapping("/admin/packages/page")
    Map<String, Object> queryPackagePage(@RequestParam("page") Integer page,
                                         @RequestParam("pageSize") Integer pageSize,
                                         @RequestParam(value = "name", required = false) String name,
                                         @RequestParam(value = "status", required = false) Integer status);

    /**
     * 后台分页查询增值权益
     */
    @GetExchange("/admin/benefits/page")
    @GetMapping("/admin/benefits/page")
    Map<String, Object> queryBenefitPage(@RequestParam("page") Integer page,
                                         @RequestParam("pageSize") Integer pageSize,
                                         @RequestParam(value = "name", required = false) String name,
                                         @RequestParam(value = "status", required = false) Integer status);

    /**
     * 后台看板：支付订单趋势（近N天已支付订单数）
     */
    @GetExchange("/admin/orders/trend")
    @GetMapping("/admin/orders/trend")
    Map<String, Object> payTrend(@RequestParam("days") Integer days);

    /**
     * 后台看板：支付方式分布（已支付订单按支付方式分组）
     */
    @GetExchange("/admin/orders/pay-type-distribution")
    @GetMapping("/admin/orders/pay-type-distribution")
    Map<String, Object> payTypeDistribution();

    /**
     * 后台查询全部套餐（含下架）
     */
    @GetExchange("/admin/packages/all")
    @GetMapping("/admin/packages/all")
    List<VipPackage> queryAllPackages();

    /**
     * 后台查询全部权益（含停用）
     */
    @GetExchange("/admin/benefits/all")
    @GetMapping("/admin/benefits/all")
    List<ValueAddedService> queryAllBenefits();
}