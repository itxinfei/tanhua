package com.xfjy.server.controller;

import com.xfjy.server.pojo.User;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.service.api.PayApi;
import com.xfjy.service.pojo.PayOrder;
import com.xfjy.service.pojo.UserVip;
import com.xfjy.service.pojo.ValueAddedService;
import com.xfjy.service.pojo.VipPackage;
import com.xfjy.service.vo.BenefitCheckVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 支付/VIP 用户端入口（gateway 路由 /users/** → server，无需改网关配置）
 */
@RestController
@RequestMapping("users/vip")
public class PayController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayController.class);

    @Autowired
    private PayApi payApi;

    /**
     * 查询上架的 VIP 套餐列表
     */
    @GetMapping("packages")
    public ResponseEntity<List<VipPackage>> queryPackages() {
        try {
            List<VipPackage> packages = this.payApi.queryPackages();
            return ResponseEntity.ok(packages);
        } catch (Exception e) {
            LOGGER.error("查询套餐异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 创建支付订单（当前登录用户下单）
     *
     * @param param 请求体：packageId - 套餐 ID
     */
    @PostMapping("order")
    public ResponseEntity<PayOrder> createOrder(@RequestBody Map<String, Object> param) {
        try {
            User user = UserThreadLocal.get();
            if (user == null || user.getId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            Long packageId = param.get("packageId") != null
                    ? Long.valueOf(param.get("packageId").toString()) : null;
            if (packageId == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            PayOrder order = this.payApi.createOrder(user.getId(), packageId);
            if (order != null) {
                return ResponseEntity.ok(order);
            }
        } catch (Exception e) {
            LOGGER.error("创建订单异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 模拟支付确认（沙箱回调入口）
     *
     * @param param 请求体：orderNo - 订单号
     */
    @PostMapping("pay/confirm")
    public ResponseEntity<Void> confirmPay(@RequestBody Map<String, String> param) {
        try {
            User user = UserThreadLocal.get();
            if (user == null || user.getId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            String orderNo = param.get("orderNo");
            if (orderNo == null || orderNo.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            Boolean success = this.payApi.confirmPay(orderNo, user.getId());
            if (Boolean.TRUE.equals(success)) {
                return ResponseEntity.ok().build();
            }
            // 订单不存在/非本人/状态不允许/已过期 → 业务失败
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("支付确认异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 查询当前登录用户 VIP 状态
     */
    @GetMapping("status")
    public ResponseEntity<UserVip> queryVipStatus() {
        try {
            User user = UserThreadLocal.get();
            if (user == null || user.getId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            UserVip userVip = this.payApi.queryVipStatus(user.getId());
            return ResponseEntity.ok(userVip);
        } catch (Exception e) {
            LOGGER.error("查询VIP状态异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 根据订单号查询订单详情
     */
    @GetMapping("order/{orderNo}")
    public ResponseEntity<PayOrder> queryOrder(@PathVariable("orderNo") String orderNo) {
        try {
            PayOrder order = this.payApi.queryOrder(orderNo);
            if (order != null) {
                return ResponseEntity.ok(order);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            LOGGER.error("查询订单异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 查询增值服务权益矩阵（P2）
     */
    @GetMapping("benefits")
    public ResponseEntity<List<ValueAddedService>> queryBenefits() {
        try {
            List<ValueAddedService> benefits = this.payApi.queryBenefits();
            return ResponseEntity.ok(benefits);
        } catch (Exception e) {
            LOGGER.error("查询权益矩阵异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 权益校验（P2）：VIP 等级是否达标 + 每日限次权益的剩余次数
     *
     * @param serviceCode 权益码（super_like/see_who_likes/unlimited_swipe/stealth_visit/exclusive_service）
     * @return 校验结果；权益码不存在返回 404
     */
    @GetMapping("benefit/check")
    public ResponseEntity<BenefitCheckVo> checkBenefit(@RequestParam("serviceCode") String serviceCode) {
        try {
            User user = UserThreadLocal.get();
            if (user == null || user.getId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            BenefitCheckVo vo = this.payApi.checkBenefit(user.getId(), serviceCode);
            if (vo != null) {
                return ResponseEntity.ok(vo);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            LOGGER.error("权益校验异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 记录权益使用（P2）：每日限次权益扣减次数
     *
     * @param param 请求体：serviceCode - 权益码
     * @return 200=扣减成功；429=今日次数已用尽；404=权益不存在
     */
    @PostMapping("benefit/usage")
    public ResponseEntity<Void> recordBenefitUsage(@RequestBody Map<String, String> param) {
        try {
            User user = UserThreadLocal.get();
            if (user == null || user.getId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            String serviceCode = param.get("serviceCode");
            if (serviceCode == null || serviceCode.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            Boolean success = this.payApi.recordBenefitUsage(user.getId(), serviceCode);
            if (Boolean.TRUE.equals(success)) {
                return ResponseEntity.ok().build();
            }
            // 权益不存在 → 404；次数用尽 → 429
            BenefitCheckVo vo = this.payApi.checkBenefit(user.getId(), serviceCode);
            if (vo == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        } catch (Exception e) {
            LOGGER.error("权益使用记录异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}