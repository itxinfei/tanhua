package com.xfjy.pay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xfjy.pay.mapper.PayOrderMapper;
import com.xfjy.pay.mapper.UserVipMapper;
import com.xfjy.pay.mapper.ValueAddedServiceMapper;
import com.xfjy.pay.mapper.VipPackageMapper;
import com.xfjy.service.pojo.PayOrder;
import com.xfjy.service.pojo.UserVip;
import com.xfjy.service.pojo.ValueAddedService;
import com.xfjy.service.pojo.VipPackage;
import com.xfjy.service.vo.BenefitCheckVo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 支付业务：套餐查询、下单、模拟支付沙箱、VIP 开通。
 * 生产环境接入真实支付网关时，仅需将 confirmPay 改为网关异步回调驱动。
 */
@Service
public class PayService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayService.class);

    /** 订单号 Redis 自增序列 key 前缀（按天分桶，避免无限增长） */
    private static final String ORDER_SEQ_PREFIX = "XFJY_ORDER_SEQ_";
    /** 订单有效期：30 分钟 */
    private static final int ORDER_EXPIRE_MINUTES = 30;

    @Autowired
    private VipPackageMapper vipPackageMapper;

    @Autowired
    private PayOrderMapper payOrderMapper;

    @Autowired
    private UserVipMapper userVipMapper;

    @Autowired
    private ValueAddedServiceMapper valueAddedServiceMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 权益每日已用次数 Redis key 前缀：XFJY_BENEFIT_USED_{userId}_{code}_{yyyyMMdd} */
    private static final String BENEFIT_USED_PREFIX = "XFJY_BENEFIT_USED_";

    /**
     * 查询上架的 VIP 套餐列表（按 sort 升序）
     */
    public List<VipPackage> queryPackages() {
        LambdaQueryWrapper<VipPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VipPackage::getStatus, 1)
                .orderByAsc(VipPackage::getSort);
        List<VipPackage> packages = this.vipPackageMapper.selectList(wrapper);
        return packages != null ? packages : Collections.emptyList();
    }

    /**
     * 创建支付订单
     */
    public PayOrder createOrder(Long userId, Long packageId) {
        if (userId == null || packageId == null) {
            return null;
        }
        VipPackage vipPackage = this.vipPackageMapper.selectById(packageId);
        if (vipPackage == null || vipPackage.getStatus() == null || vipPackage.getStatus() != 1) {
            LOGGER.warn("套餐不存在或已下架, packageId = {}", packageId);
            return null;
        }

        Date now = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.MINUTE, ORDER_EXPIRE_MINUTES);

        PayOrder order = new PayOrder();
        order.setOrderNo(this.generateOrderNo());
        order.setUserId(userId);
        order.setPackageId(packageId);
        order.setPackageName(vipPackage.getName());
        order.setAmount(vipPackage.getPrice());
        order.setStatus("PENDING");
        order.setPayType("SANDBOX");
        order.setExpireTime(calendar.getTime());
        order.setCreated(now);
        order.setUpdated(now);

        int rows = this.payOrderMapper.insert(order);
        if (rows <= 0) {
            LOGGER.error("订单写入失败, userId = {}, packageId = {}", userId, packageId);
            return null;
        }
        return order;
    }

    /**
     * 模拟支付确认（沙箱回调）：订单 PENDING → PAID → 开通 VIP。
     * 生产环境由真实支付网关异步回调触发，替换本方法的事务体即可。
     */
    @Transactional(rollbackFor = Exception.class)
    public Boolean confirmPay(String orderNo, Long userId) {
        if (StringUtils.isBlank(orderNo) || userId == null) {
            return false;
        }
        PayOrder order = this.queryOrder(orderNo);
        if (order == null || !userId.equals(order.getUserId())) {
            LOGGER.warn("订单不存在或不归属当前用户, orderNo = {}, userId = {}", orderNo, userId);
            return false;
        }
        if (!"PENDING".equals(order.getStatus())) {
            LOGGER.warn("订单状态不允许支付, orderNo = {}, status = {}", orderNo, order.getStatus());
            return false;
        }
        Date now = new Date();
        if (order.getExpireTime() != null && now.after(order.getExpireTime())) {
            // 订单已过期，标记后拒绝支付
            PayOrder expired = new PayOrder();
            expired.setId(order.getId());
            expired.setStatus("EXPIRED");
            expired.setUpdated(now);
            this.payOrderMapper.updateById(expired);
            LOGGER.warn("订单已过期, orderNo = {}", orderNo);
            return false;
        }

        // 1. 订单标记已支付
        PayOrder paid = new PayOrder();
        paid.setId(order.getId());
        paid.setStatus("PAID");
        paid.setPaidTime(now);
        paid.setUpdated(now);
        this.payOrderMapper.updateById(paid);

        // 2. 开通 VIP（按套餐天数延展到期时间）
        VipPackage vipPackage = this.vipPackageMapper.selectById(order.getPackageId());
        if (vipPackage == null) {
            LOGGER.error("支付成功但套餐已丢失, orderNo = {}, packageId = {}", orderNo, order.getPackageId());
            // 套餐丢失属于数据异常，抛出触发事务回滚，避免钱到账未开通
            throw new IllegalStateException("套餐数据缺失: " + order.getPackageId());
        }
        this.openVip(userId, vipPackage, now);

        LOGGER.info("模拟支付成功, orderNo = {}, userId = {}, package = {}",
                orderNo, userId, vipPackage.getName());
        return true;
    }

    /**
     * 开通/续费 VIP：未过期则在原到期时间上顺延，已过期则从当前时间起算；
     * 等级取本次套餐等级与当前等级的较高者。
     */
    private void openVip(Long userId, VipPackage vipPackage, Date now) {
        UserVip current = this.queryVipRecord(userId);
        Date baseTime = now;
        int level = vipPackage.getLevel();
        if (current != null && current.getExpireTime() != null && now.before(current.getExpireTime())) {
            // 当前 VIP 未过期：到期时间顺延，等级保留较高者
            baseTime = current.getExpireTime();
            if (current.getLevel() != null && current.getLevel() > level) {
                level = current.getLevel();
            }
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(baseTime);
        calendar.add(Calendar.DAY_OF_MONTH, vipPackage.getDurationDays());
        Date newExpireTime = calendar.getTime();

        if (current == null) {
            UserVip userVip = new UserVip();
            userVip.setUserId(userId);
            userVip.setLevel(level);
            userVip.setExpireTime(newExpireTime);
            userVip.setSource("PAY");
            userVip.setCreated(now);
            userVip.setUpdated(now);
            this.userVipMapper.insert(userVip);
        } else {
            UserVip update = new UserVip();
            update.setId(current.getId());
            update.setLevel(level);
            update.setExpireTime(newExpireTime);
            update.setSource("PAY");
            update.setUpdated(now);
            this.userVipMapper.updateById(update);
        }
    }

    /**
     * 查询用户 VIP 状态（非会员返回 level=0 的默认记录，便于前端统一处理）
     */
    public UserVip queryVipStatus(Long userId) {
        if (userId == null) {
            UserVip empty = new UserVip();
            empty.setLevel(0);
            return empty;
        }
        UserVip userVip = this.queryVipRecord(userId);
        if (userVip == null) {
            UserVip empty = new UserVip();
            empty.setUserId(userId);
            empty.setLevel(0);
            return empty;
        }
        // 已过期归零
        if (userVip.getExpireTime() != null && new Date().after(userVip.getExpireTime())) {
            userVip.setLevel(0);
        }
        return userVip;
    }

    /**
     * 查询用户 VIP 原始记录（不过滤过期状态）
     */
    private UserVip queryVipRecord(Long userId) {
        LambdaQueryWrapper<UserVip> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserVip::getUserId, userId);
        return this.userVipMapper.selectOne(wrapper);
    }

    /**
     * 根据订单号查询订单详情
     */
    public PayOrder queryOrder(String orderNo) {
        if (StringUtils.isBlank(orderNo)) {
            return null;
        }
        LambdaQueryWrapper<PayOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayOrder::getOrderNo, orderNo);
        return this.payOrderMapper.selectOne(wrapper);
    }

    /**
     * 生成订单号：VIP + yyyyMMdd + 6 位自增序列（Redis 保证分布式唯一）
     */
    private String generateOrderNo() {
        String day = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
        Long seq = this.redisTemplate.opsForValue().increment(ORDER_SEQ_PREFIX + day);
        if (seq == null) {
            // Redis 不可用时退化为时间戳，保证可用性（唯一性降级）
            seq = System.currentTimeMillis() % 1000000L;
        }
        return "VIP" + day + String.format("%06d", seq);
    }

    // ==================== P2 增值服务权益矩阵 ====================

    /**
     * 查询上架的权益列表（按 sort 升序）
     */
    public List<ValueAddedService> queryBenefits() {
        LambdaQueryWrapper<ValueAddedService> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ValueAddedService::getStatus, 1)
                .orderByAsc(ValueAddedService::getSort);
        List<ValueAddedService> list = this.valueAddedServiceMapper.selectList(wrapper);
        return list != null ? list : Collections.emptyList();
    }

    /**
     * 权益校验：VIP 等级是否达标 + 每日限次权益的剩余次数
     *
     * @param userId      用户 id
     * @param serviceCode 权益码
     * @return 校验结果；权益码不存在返回 null
     */
    public BenefitCheckVo checkBenefit(Long userId, String serviceCode) {
        if (userId == null || StringUtils.isBlank(serviceCode)) {
            return null;
        }
        ValueAddedService service = queryBenefitByCode(serviceCode);
        if (service == null) {
            return null;
        }

        UserVip vip = this.queryVipStatus(userId);
        int userLevel = vip.getLevel() != null ? vip.getLevel() : 0;
        boolean hasBenefit = userLevel >= service.getVipLevelRequired();

        BenefitCheckVo vo = new BenefitCheckVo();
        vo.setServiceCode(serviceCode);
        vo.setName(service.getName());
        vo.setVipLevelRequired(service.getVipLevelRequired());
        vo.setUserVipLevel(userLevel);
        vo.setHasBenefit(hasBenefit);

        // 有日限的权益补充今日已用/剩余次数
        if (service.getDailyLimit() != null) {
            int usedToday = getUsedToday(userId, serviceCode);
            vo.setDailyLimit(service.getDailyLimit());
            vo.setUsedToday(usedToday);
            vo.setRemainingToday(Math.max(0, service.getDailyLimit() - usedToday));
        }
        return vo;
    }

    /**
     * 记录权益使用（每日限次权益扣减次数）
     *
     * @return true=扣减成功或权益不限次；false=今日次数已用尽或权益不存在
     */
    public boolean recordBenefitUsage(Long userId, String serviceCode) {
        if (userId == null || StringUtils.isBlank(serviceCode)) {
            return false;
        }
        ValueAddedService service = queryBenefitByCode(serviceCode);
        if (service == null) {
            return false;
        }
        if (service.getDailyLimit() == null) {
            return true; // 不限次权益
        }
        String key = benefitUsedKey(userId, serviceCode);
        int usedToday = getUsedToday(userId, serviceCode);
        if (usedToday >= service.getDailyLimit()) {
            return false; // 今日已用尽
        }
        Long used = this.redisTemplate.opsForValue().increment(key);
        if (used != null && used == 1L) {
            // 首次使用，TTL 设到次日零点（避免手动清理）
            java.util.Date nextMidnight = java.util.Date.from(
                    LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1)
                            .atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
            this.redisTemplate.expireAt(key, nextMidnight);
        }
        return true;
    }

    private ValueAddedService queryBenefitByCode(String serviceCode) {
        LambdaQueryWrapper<ValueAddedService> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ValueAddedService::getServiceCode, serviceCode)
                .eq(ValueAddedService::getStatus, 1);
        return this.valueAddedServiceMapper.selectOne(wrapper);
    }

    private int getUsedToday(Long userId, String serviceCode) {
        String used = this.redisTemplate.opsForValue().get(benefitUsedKey(userId, serviceCode));
        return used != null ? Integer.parseInt(used) : 0;
    }

    private String benefitUsedKey(Long userId, String serviceCode) {
        String day = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
        return BENEFIT_USED_PREFIX + userId + "_" + serviceCode + "_" + day;
    }
}