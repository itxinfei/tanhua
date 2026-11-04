package com.xfjy.service.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.pay.mapper.PayOrderMapper;
import com.xfjy.pay.mapper.ValueAddedServiceMapper;
import com.xfjy.pay.mapper.VipPackageMapper;
import com.xfjy.pay.service.PayService;
import com.xfjy.service.pojo.PayOrder;
import com.xfjy.service.pojo.UserVip;
import com.xfjy.service.pojo.ValueAddedService;
import com.xfjy.service.pojo.VipPackage;
import com.xfjy.service.vo.BenefitCheckVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付服务契约实现（由 server 编排层通过 @HttpExchange 代理调用）
 */
@RestController
@RequestMapping("/api/pay")
public class PayApiImpl implements PayApi {

    @Autowired
    private PayService payService;

    @Autowired
    private PayOrderMapper payOrderMapper;

    @Autowired
    private VipPackageMapper vipPackageMapper;

    @Autowired
    private ValueAddedServiceMapper valueAddedServiceMapper;

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
    @PostMapping("/cancelOrder")
    public Boolean cancelOrder(@RequestParam("orderNo") String orderNo,
                               @RequestParam("userId") Long userId) {
        return this.payService.cancelOrder(orderNo, userId);
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

    @Override
    @GetMapping("/benefits")
    public List<ValueAddedService> queryBenefits() {
        return this.payService.queryBenefits();
    }

    @Override
    @GetMapping("/benefit/check")
    public BenefitCheckVo checkBenefit(@RequestParam("userId") Long userId,
                                       @RequestParam("serviceCode") String serviceCode) {
        return this.payService.checkBenefit(userId, serviceCode);
    }

    @Override
    @PostMapping("/benefit/usage")
    public Boolean recordBenefitUsage(@RequestParam("userId") Long userId,
                                      @RequestParam("serviceCode") String serviceCode) {
        return this.payService.recordBenefitUsage(userId, serviceCode);
    }

    // ==================== 后台管理接口实现 ====================

    @Override
    @GetMapping("/admin/orders")
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

    @Override
    @GetMapping("/admin/orders/summary")
    public Map<String, Object> queryOrderSummary(String status, String startDate, String endDate) {
        QueryWrapper<PayOrder> wrapper = new QueryWrapper<>();
        applyDateRange(wrapper, startDate, endDate);
        if (StringUtils.isNotBlank(status)) {
            wrapper.eq("status", status);
        }
        long total = this.payOrderMapper.selectCount(wrapper);

        // 汇总指标（已支付数/待支付数/总金额）只受日期范围限制，不受 status 筛选影响，
        // 始终反映全局概况；status 筛选仅作用于上面的 total。
        QueryWrapper<PayOrder> paidWrapper = new QueryWrapper<>();
        applyDateRange(paidWrapper, startDate, endDate);
        paidWrapper.eq("status", "PAID");
        long paidCount = this.payOrderMapper.selectCount(paidWrapper);

        QueryWrapper<PayOrder> pendingWrapper = new QueryWrapper<>();
        applyDateRange(pendingWrapper, startDate, endDate);
        pendingWrapper.eq("status", "PENDING");
        long pendingCount = this.payOrderMapper.selectCount(pendingWrapper);

        QueryWrapper<PayOrder> amountWrapper = new QueryWrapper<>();
        applyDateRange(amountWrapper, startDate, endDate);
        amountWrapper.eq("status", "PAID")
                .select("COALESCE(SUM(amount), 0) AS total_amount");
        List<Map<String, Object>> maps = this.payOrderMapper.selectMaps(amountWrapper);
        long totalAmount = 0L;
        if (maps != null && !maps.isEmpty()) {
            Map<String, Object> row = maps.get(0);
            totalAmount = row != null && row.get("total_amount") != null
                    ? ((Number) row.get("total_amount")).longValue() : 0L;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("paidCount", paidCount);
        result.put("pendingCount", pendingCount);
        result.put("totalAmount", totalAmount);
        return result;
    }

    private void applyDateRange(QueryWrapper<PayOrder> wrapper, String startDate, String endDate) {
        // created 列为 datetime，必须传 Date 对象而非毫秒戳（long），否则 MySQL 隐式转换导致范围比较错误
        if (StringUtils.isNotBlank(startDate)) {
            wrapper.ge("created", java.sql.Date.valueOf(startDate));
        }
        if (StringUtils.isNotBlank(endDate)) {
            // endDate 当天 23:59:59.999
            wrapper.le("created", new java.util.Date(java.sql.Date.valueOf(endDate).getTime() + 86399999L));
        }
    }

    @Override
    @PostMapping("/admin/packages")
    public Boolean createPackage(String name, Integer level, Long price, Long originalPrice,
                                  Integer durationDays, String description, Integer sort) {
        if (StringUtils.isBlank(name) || level == null || price == null || durationDays == null) {
            return false;
        }
        VipPackage entity = new VipPackage();
        entity.setName(name);
        entity.setLevel(level);
        entity.setPrice(price);
        entity.setOriginalPrice(originalPrice);
        entity.setDurationDays(durationDays);
        entity.setDescription(description);
        entity.setSort(sort == null ? 0 : sort);
        entity.setStatus(1);
        return this.vipPackageMapper.insert(entity) > 0;
    }

    @Override
    @PutMapping("/admin/packages/{id}")
    public Boolean updatePackage(Long id, Map<String, Object> body) {
        VipPackage entity = this.vipPackageMapper.selectById(id);
        if (entity == null) {
            return false;
        }
        if (body.containsKey("name") && body.get("name") != null) {
            entity.setName(String.valueOf(body.get("name")));
        }
        if (body.containsKey("level") && body.get("level") != null) {
            entity.setLevel(Integer.valueOf(String.valueOf(body.get("level"))));
        }
        if (body.containsKey("price") && body.get("price") != null) {
            entity.setPrice(Long.valueOf(String.valueOf(body.get("price"))));
        }
        if (body.containsKey("originalPrice")) {
            Object originalPrice = body.get("originalPrice");
            entity.setOriginalPrice(originalPrice == null ? null : Long.valueOf(String.valueOf(originalPrice)));
        }
        if (body.containsKey("durationDays") && body.get("durationDays") != null) {
            entity.setDurationDays(Integer.valueOf(String.valueOf(body.get("durationDays"))));
        }
        if (body.containsKey("description")) {
            entity.setDescription(body.get("description") == null ? null : String.valueOf(body.get("description")));
        }
        if (body.containsKey("sort") && body.get("sort") != null) {
            entity.setSort(Integer.valueOf(String.valueOf(body.get("sort"))));
        }
        return this.vipPackageMapper.updateById(entity) > 0;
    }

    @Override
    @PostMapping("/admin/packages/{id}/status")
    public Boolean togglePackage(Long id, Integer status) {
        VipPackage entity = this.vipPackageMapper.selectById(id);
        if (entity == null || status == null) {
            return false;
        }
        entity.setStatus(status);
        return this.vipPackageMapper.updateById(entity) > 0;
    }

    @Override
    @PutMapping("/admin/benefits/{id}")
    public Boolean updateBenefit(Long id, Map<String, Object> body) {
        ValueAddedService entity = this.valueAddedServiceMapper.selectById(id);
        if (entity == null) {
            return false;
        }
        if (body.containsKey("vipLevelRequired") && body.get("vipLevelRequired") != null) {
            entity.setVipLevelRequired(Integer.valueOf(String.valueOf(body.get("vipLevelRequired"))));
        }
        if (body.containsKey("dailyLimit")) {
            Object dailyLimit = body.get("dailyLimit");
            entity.setDailyLimit(dailyLimit == null ? null : Integer.valueOf(String.valueOf(dailyLimit)));
        }
        if (body.containsKey("description")) {
            entity.setDescription(body.get("description") == null ? null : String.valueOf(body.get("description")));
        }
        if (body.containsKey("sort") && body.get("sort") != null) {
            entity.setSort(Integer.valueOf(String.valueOf(body.get("sort"))));
        }
        return this.valueAddedServiceMapper.updateById(entity) > 0;
    }

    @Override
    @PostMapping("/admin/benefits/{id}/status")
    public Boolean toggleBenefit(Long id, Integer status) {
        ValueAddedService entity = this.valueAddedServiceMapper.selectById(id);
        if (entity == null || status == null) {
            return false;
        }
        entity.setStatus(status);
        return this.valueAddedServiceMapper.updateById(entity) > 0;
    }

    @Override
    @GetMapping("/admin/packages/page")
    public Map<String, Object> queryPackagePage(Integer page, Integer pageSize,
                                                  String name, Integer status) {
        QueryWrapper<VipPackage> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(name)) {
            wrapper.like("name", name);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByAsc("level");

        IPage<VipPackage> packagePage = this.vipPackageMapper.selectPage(new Page<>(page, pageSize), wrapper);
        Map<String, Object> result = new HashMap<>();
        result.put("total", packagePage.getTotal());
        result.put("list", packagePage.getRecords());
        return result;
    }

    @Override
    @GetMapping("/admin/benefits/page")
    public Map<String, Object> queryBenefitPage(Integer page, Integer pageSize,
                                                  String name, Integer status) {
        QueryWrapper<ValueAddedService> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(name)) {
            wrapper.like("name", name);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByAsc("vip_level_required");

        IPage<ValueAddedService> benefitPage = this.valueAddedServiceMapper.selectPage(new Page<>(page, pageSize), wrapper);
        Map<String, Object> result = new HashMap<>();
        result.put("total", benefitPage.getTotal());
        result.put("list", benefitPage.getRecords());
        return result;
    }

    @Override
    @GetMapping("/admin/orders/trend")
    public Map<String, Object> payTrend(Integer days) {
        if (days == null || days <= 0) {
            days = 7;
        }
        List<String> dates = new ArrayList<>();
        List<Long> orderList = new ArrayList<>();
        List<Long> amountList = new ArrayList<>();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -(days - 1));
        for (int i = 0; i < days; i++) {
            Date dayStart = cal.getTime();
            Calendar endCal = (Calendar) cal.clone();
            endCal.add(Calendar.DAY_OF_MONTH, 1);
            Date dayEnd = endCal.getTime();
            dates.add(sdf.format(dayStart));
            orderList.add(this.countPaidOrders(dayStart, dayEnd));
            amountList.add(this.sumPaidAmount(dayStart, dayEnd));
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("orderList", orderList);
        // 每日已支付金额（单位：分），看板收入曲线使用
        result.put("amountList", amountList);
        return result;
    }

    @Override
    @GetMapping("/admin/orders/revenue-overview")
    public Map<String, Object> revenueOverview() {
        // 今日口径：status=PAID 且 paid_time 落在今日；累计口径：全部 PAID 订单
        Date todayStart = java.sql.Date.valueOf(java.time.LocalDate.now());
        QueryWrapper<PayOrder> todayWrapper = new QueryWrapper<>();
        todayWrapper.eq("status", "PAID").ge("paid_time", todayStart);
        long todayOrders = this.payOrderMapper.selectCount(todayWrapper);

        QueryWrapper<PayOrder> todayAggWrapper = new QueryWrapper<>();
        todayAggWrapper.eq("status", "PAID").ge("paid_time", todayStart)
                .select("COUNT(DISTINCT user_id) AS pay_users", "COALESCE(SUM(amount), 0) AS total_amount");
        Map<String, Object> todayAgg = this.firstAggRow(todayAggWrapper);

        QueryWrapper<PayOrder> totalWrapper = new QueryWrapper<>();
        totalWrapper.eq("status", "PAID");
        long totalOrders = this.payOrderMapper.selectCount(totalWrapper);

        QueryWrapper<PayOrder> totalAggWrapper = new QueryWrapper<>();
        totalAggWrapper.eq("status", "PAID")
                .select("COUNT(DISTINCT user_id) AS pay_users", "COALESCE(SUM(amount), 0) AS total_amount");
        Map<String, Object> totalAgg = this.firstAggRow(totalAggWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("todayAmount", aggLong(todayAgg, "total_amount"));
        result.put("todayOrders", todayOrders);
        result.put("todayPayUsers", aggLong(todayAgg, "pay_users"));
        result.put("totalAmount", aggLong(totalAgg, "total_amount"));
        result.put("totalOrders", totalOrders);
        result.put("totalPayUsers", aggLong(totalAgg, "pay_users"));
        return result;
    }

    /** 统计区间内已支付订单数（paid_time 半开区间 [start, end)） */
    private long countPaidOrders(Date start, Date end) {
        QueryWrapper<PayOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "PAID")
                .ge("paid_time", start).lt("paid_time", end);
        return this.payOrderMapper.selectCount(wrapper);
    }

    /** 汇总区间内已支付金额（单位：分） */
    private long sumPaidAmount(Date start, Date end) {
        QueryWrapper<PayOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "PAID")
                .ge("paid_time", start).lt("paid_time", end)
                .select("COALESCE(SUM(amount), 0) AS total_amount");
        return aggLong(this.firstAggRow(wrapper), "total_amount");
    }

    /** 取无 GROUP BY 聚合查询的唯一一行，表为空时返回空 Map */
    private Map<String, Object> firstAggRow(QueryWrapper<PayOrder> wrapper) {
        List<Map<String, Object>> rows = this.payOrderMapper.selectMaps(wrapper);
        return rows == null || rows.isEmpty() ? Map.of() : rows.get(0);
    }

    /** 聚合列在不同驱动版本下可能返回 Long/BigInteger/BigDecimal，统一转 long */
    private static long aggLong(Map<String, Object> row, String column) {
        Object value = row == null ? null : row.get(column);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    @Override
    @GetMapping("/admin/orders/pay-type-distribution")
    public Map<String, Object> payTypeDistribution() {
        QueryWrapper<PayOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "PAID");
        List<PayOrder> orders = this.payOrderMapper.selectList(wrapper);
        long alipay = 0, wechat = 0, sandbox = 0;
        for (PayOrder order : orders) {
            String type = order.getPayType();
            if ("ALIPAY".equals(type)) {
                alipay++;
            } else if ("WECHAT".equals(type)) {
                wechat++;
            } else {
                sandbox++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("alipay", alipay);
        result.put("wechat", wechat);
        result.put("sandbox", sandbox);
        return result;
    }

    @Override
    @GetMapping("/admin/packages/all")
    public List<VipPackage> queryAllPackages() {
        QueryWrapper<VipPackage> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("level");
        return this.vipPackageMapper.selectList(wrapper);
    }

    @Override
    @GetMapping("/admin/benefits/all")
    public List<ValueAddedService> queryAllBenefits() {
        QueryWrapper<ValueAddedService> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("vip_level_required");
        return this.valueAddedServiceMapper.selectList(wrapper);
    }
}