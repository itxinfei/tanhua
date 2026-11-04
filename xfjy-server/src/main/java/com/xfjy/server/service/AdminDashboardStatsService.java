package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.server.mapper.ReportMapper;
import com.xfjy.server.mapper.ReviewMapper;
import com.xfjy.server.mapper.UserMapper;
import com.xfjy.server.pojo.Report;
import com.xfjy.server.pojo.Review;
import com.xfjy.service.api.PayApi;
import com.xfjy.service.api.SwipeLogApi;
import com.xfjy.service.api.UserInfoApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台数据看板增强统计服务（2026-11 看板优化新增）
 *
 * <p>与 {@link AdminDashboardService} 的分工：旧服务保留早期看板端点不动，
 * 本类承载新版看板的实时在线、DAU/留存、匹配漏斗、人口属性、收入、内容安全统计。</p>
 *
 * <p>数据归属：滑动/人口/收入均通过领域服务 RPC 聚合（表在写入方服务查询），
 * DAU/留存/在线读取 BFF 自身埋点的 Redis key，审核/举报查 BFF 独占表。</p>
 */
@Service
public class AdminDashboardStatsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminDashboardStatsService.class);

    /** 在线用户 ZSET key，与 TokenInterceptor / AdminDashboardService 保持一致 */
    private static final String ONLINE_KEY = "XFJY_ONLINE_USERS";

    /** 在线用户心跳有效期：5 分钟无请求视为离线 */
    private static final long ONLINE_TTL_MS = 5 * 60 * 1000L;

    /** 日活用户 SET key 前缀（XFJY_ACTIVE_yyyyMMdd），与 TokenInterceptor 保持一致 */
    private static final String ACTIVE_KEY_PREFIX = "XFJY_ACTIVE_";

    /** 留存计算档位（天） */
    private static final int[] RETENTION_DAYS = {1, 3, 7, 30};

    /** 看板趋势日期格式（MM-dd），线程安全 */
    private static final DateTimeFormatter MMDD_FMT = DateTimeFormatter.ofPattern("MM-dd");

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SwipeLogApi swipeLogApi;

    @Autowired
    private UserInfoApi userInfoApi;

    @Autowired
    private PayApi payApi;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 实时在线概览：当前在线数、今日峰值、今日活跃 DAU
     */
    public Map<String, Object> online() {
        Map<String, Object> result = new HashMap<>();

        long onlineUsers = 0;
        try {
            long cutoff = System.currentTimeMillis() - ONLINE_TTL_MS;
            var members = this.redisTemplate.opsForZSet().rangeByScore(ONLINE_KEY, cutoff, Double.MAX_VALUE);
            onlineUsers = members != null ? members.size() : 0;
        } catch (Exception e) {
            LOGGER.warn("统计当前在线用户失败", e);
        }
        result.put("onlineUsers", onlineUsers);

        // 今日峰值：取当日已记录最大值，并在当前在线数创新高时回写（与旧端点同逻辑）
        long peakToday = onlineUsers;
        try {
            String peakKey = "XFJY_PEAK_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String peakVal = this.redisTemplate.opsForValue().get(peakKey);
            if (peakVal != null) {
                peakToday = Math.max(peakToday, Long.parseLong(peakVal));
            }
            if (onlineUsers > peakToday) {
                this.redisTemplate.opsForValue().set(peakKey, String.valueOf(onlineUsers));
            }
        } catch (Exception e) {
            LOGGER.warn("统计在线峰值失败", e);
        }
        result.put("peakToday", peakToday);
        result.put("todayActive", countActiveSet(LocalDate.now()));
        return result;
    }

    /**
     * 活跃与留存：
     * <ul>
     *   <li>dates/dauList：近 N 天每日活跃用户数；埋点未覆盖的历史日为 null（区别于真实 0）</li>
     *   <li>todayActive：今日活跃（与 dauList 最后一项同源，方便前端直接取）</li>
     *   <li>retention：次日/3/7/30 日留存，cohort=N 天前注册用户，retained=其中今日仍活跃人数</li>
     * </ul>
     */
    public Map<String, Object> activity(Integer days) {
        if (days == null || days <= 0) {
            days = 7;
        }
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1L);

        // pipeline 批量 SCARD，把 N 次 Redis 往返压成 1 次
        List<String> activeKeys = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            activeKeys.add(activeKey(startDate.plusDays(i)));
        }
        List<Long> counts = pipelineSize(activeKeys);

        List<String> dates = new ArrayList<>(days);
        List<Long> dauList = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            dates.add(startDate.plusDays(i).format(MMDD_FMT));
            long size = counts.get(i) != null ? counts.get(i) : 0L;
            // 日活 SET 不存在（埋点未覆盖）时 SCARD=0，转 null 让前端显示"数据积累中"而非伪造的 0
            dauList.add(size > 0 ? size : null);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("dauList", dauList);
        result.put("todayActive", countActiveSet(today));
        result.put("retention", buildRetention(today));
        return result;
    }

    /**
     * 匹配统计：今日漏斗 + 近 N 天每日序列（数据源 user-service tb_swipe_log）
     */
    public Map<String, Object> matchStats(Integer days) {
        final int queryDays = days == null || days <= 0 ? 7 : days;
        Map<String, Object> result = new HashMap<>();
        result.put("today", safeRpc(() -> this.swipeLogApi.adminTodayFunnel(), new HashMap<>()));
        result.put("daily", safeRpc(() -> this.swipeLogApi.adminDailyStats(queryDays), new HashMap<>()));
        return result;
    }

    /**
     * 人口属性聚合（数据源 user-service tb_user_info）：性别/年龄段/城市 TOP10
     */
    public Map<String, Object> demographics() {
        return safeRpc(() -> this.userInfoApi.adminDemographics(), new HashMap<>());
    }

    /**
     * 收入与付费：收入概览 + 近 N 天订单/金额趋势 + 支付方式分布 + 付费率/今日 ARPPU
     * 金额单位均为分，付费率/ARPPU 在 BFF 用总用户数换算
     */
    public Map<String, Object> revenue(Integer days) {
        final int queryDays = days == null || days <= 0 ? 7 : days;
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> overview = safeRpc(this.payApi::revenueOverview, new HashMap<>());
        Map<String, Object> trend = safeRpc(() -> this.payApi.payTrend(queryDays), new HashMap<>());
        Map<String, Object> payType = safeRpc(this.payApi::payTypeDistribution, new HashMap<>());
        result.put("overview", overview);
        result.put("trend", trend);
        result.put("payType", payType);

        long totalUsers = countTotalUsers();
        result.put("totalUsers", totalUsers);
        long totalPayUsers = toLong(overview.get("totalPayUsers"));
        result.put("payRate", totalUsers > 0 ? Math.round(totalPayUsers * 1000.0 / totalUsers) / 10.0 : null);

        // 今日 ARPPU = 今日已支付金额 / 今日付费人数（分）
        long todayPayUsers = toLong(overview.get("todayPayUsers"));
        long todayAmount = toLong(overview.get("todayAmount"));
        result.put("todayArppu", todayPayUsers > 0 ? Math.round((double) todayAmount / todayPayUsers) : null);
        return result;
    }

    /**
     * 内容安全统计：审核（tb_review）与举报（tb_report）的存量、今日处理量、近 N 天每日序列与分布
     */
    public Map<String, Object> moderation(Integer days) {
        if (days == null || days <= 0) {
            days = 7;
        }
        LocalDate today = LocalDate.now();
        Date start = Date.from(today.minusDays(days - 1L).atStartOfDay(ZoneId.systemDefault()).toInstant());
        String todayStr = today.toString();

        // ==================== 审核 ====================
        Map<String, Object> review = new HashMap<>();
        review.put("pending", this.reviewMapper.selectCount(new QueryWrapper<Review>().eq("status", "PENDING")));

        // 近 N 天每日新增（按 created）与每日处理（按 reviewed_at + 终态状态）
        Map<String, Long> reviewCreated = dateCountMap(this.reviewMapper.selectMaps(
                this.<Review>dateGroupWrapper("DATE(created)", "created", start)));
        Map<String, Map<String, Long>> reviewHandled = dateStatusCountMap(this.reviewMapper.selectMaps(
                this.<Review>dateStatusGroupWrapper("DATE(reviewed_at)", "reviewed_at", start,
                        "APPROVED", "REJECTED")));

        List<String> dates = buildDateLabels(today, days);
        List<Long> reviewCreatedList = new ArrayList<>(days);
        List<Long> reviewApprovedList = new ArrayList<>(days);
        List<Long> reviewRejectedList = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate day = today.minusDays(days - 1L - i);
            String key = day.toString();
            reviewCreatedList.add(reviewCreated.getOrDefault(key, 0L));
            Map<String, Long> handled = reviewHandled.getOrDefault(key, Map.of());
            reviewApprovedList.add(handled.getOrDefault("APPROVED", 0L));
            reviewRejectedList.add(handled.getOrDefault("REJECTED", 0L));
        }
        Map<String, Object> reviewDaily = new HashMap<>();
        reviewDaily.put("dates", dates);
        reviewDaily.put("created", reviewCreatedList);
        reviewDaily.put("approved", reviewApprovedList);
        reviewDaily.put("rejected", reviewRejectedList);
        review.put("daily", reviewDaily);
        review.put("todayApproved", reviewHandled.getOrDefault(todayStr, Map.of()).getOrDefault("APPROVED", 0L));
        review.put("todayRejected", reviewHandled.getOrDefault(todayStr, Map.of()).getOrDefault("REJECTED", 0L));
        review.put("pendingTypes", groupCount(this.reviewMapper.selectMaps(
                new QueryWrapper<Review>().select("type AS name", "COUNT(*) AS cnt")
                        .eq("status", "PENDING").groupBy("type")), "name"));

        // ==================== 举报 ====================
        Map<String, Object> report = new HashMap<>();
        report.put("pending", this.reportMapper.selectCount(new QueryWrapper<Report>().eq("status", "PENDING")));
        report.put("processing", this.reportMapper.selectCount(new QueryWrapper<Report>().eq("status", "PROCESSING")));

        Map<String, Long> reportCreated = dateCountMap(this.reportMapper.selectMaps(
                this.<Report>dateGroupWrapper("DATE(created)", "created", start)));
        Map<String, Map<String, Long>> reportHandled = dateStatusCountMap(this.reportMapper.selectMaps(
                this.<Report>dateStatusGroupWrapper("DATE(handled_at)", "handled_at", start,
                        "RESOLVED", "DISMISSED")));

        List<Long> reportCreatedList = new ArrayList<>(days);
        List<Long> reportResolvedList = new ArrayList<>(days);
        List<Long> reportDismissedList = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate day = today.minusDays(days - 1L - i);
            String key = day.toString();
            reportCreatedList.add(reportCreated.getOrDefault(key, 0L));
            Map<String, Long> handled = reportHandled.getOrDefault(key, Map.of());
            reportResolvedList.add(handled.getOrDefault("RESOLVED", 0L));
            reportDismissedList.add(handled.getOrDefault("DISMISSED", 0L));
        }
        Map<String, Object> reportDaily = new HashMap<>();
        reportDaily.put("dates", dates);
        reportDaily.put("created", reportCreatedList);
        reportDaily.put("resolved", reportResolvedList);
        reportDaily.put("dismissed", reportDismissedList);
        report.put("daily", reportDaily);
        report.put("todayNew", reportCreated.getOrDefault(todayStr, 0L));
        report.put("todayResolved", reportHandled.getOrDefault(todayStr, Map.of()).getOrDefault("RESOLVED", 0L));
        report.put("todayDismissed", reportHandled.getOrDefault(todayStr, Map.of()).getOrDefault("DISMISSED", 0L));
        report.put("targetTypes", groupCount(this.reportMapper.selectMaps(
                new QueryWrapper<Report>().select("target_type AS name", "COUNT(*) AS cnt")
                        .ge("created", start).groupBy("target_type")), "name"));

        Map<String, Object> result = new HashMap<>();
        result.put("review", review);
        result.put("report", report);
        return result;
    }

    // ==================== 留存计算 ====================

    /**
     * 构造 N 日留存：cohort 为 N 天前当天注册的用户 ID，retained 为其中今日仍在活跃 SET 中的人数
     */
    private List<Map<String, Object>> buildRetention(LocalDate today) {
        String todayActiveKey = activeKey(today);
        List<Map<String, Object>> retention = new ArrayList<>(RETENTION_DAYS.length);
        for (int n : RETENTION_DAYS) {
            LocalDate cohortDate = today.minusDays(n);
            Date dayStart = Date.from(cohortDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date dayEnd = Date.from(cohortDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

            QueryWrapper<com.xfjy.server.pojo.User> wrapper = new QueryWrapper<>();
            wrapper.select("id").ge("created", dayStart).lt("created", dayEnd);
            List<Object> cohortIds = this.userMapper.selectObjs(wrapper);

            Map<String, Object> item = new HashMap<>();
            item.put("label", n == 1 ? "次日" : n + "日");
            item.put("days", n);
            item.put("cohortSize", cohortIds.size());
            if (cohortIds.isEmpty()) {
                item.put("retained", null);
                item.put("rate", null);
            } else {
                long retained = countMembers(todayActiveKey, cohortIds);
                item.put("retained", retained);
                item.put("rate", Math.round(retained * 1000.0 / cohortIds.size()) / 10.0);
            }
            retention.add(item);
        }
        return retention;
    }

    /** pipeline 批量 SISMEMBER，统计 cohort 中今日活跃的人数 */
    private long countMembers(String setKey, List<Object> userIds) {
        try {
            @SuppressWarnings("unchecked")
            List<Object> results = this.redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings("rawtypes")
                public Object execute(RedisOperations operations) {
                    for (Object id : userIds) {
                        operations.opsForSet().isMember(setKey, String.valueOf(id));
                    }
                    return null;
                }
            });
            long count = 0;
            if (results != null) {
                for (Object r : results) {
                    if (Boolean.TRUE.equals(r)) {
                        count++;
                    }
                }
            }
            return count;
        } catch (Exception e) {
            LOGGER.warn("留存活跃人数统计失败 key={}", setKey, e);
            return 0;
        }
    }

    // ==================== Redis 辅助 ====================

    private static String activeKey(LocalDate date) {
        return ACTIVE_KEY_PREFIX + date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private long countActiveSet(LocalDate date) {
        try {
            Long size = this.redisTemplate.opsForSet().size(activeKey(date));
            return size != null ? size : 0L;
        } catch (Exception e) {
            LOGGER.warn("日活统计失败 date={}", date, e);
            return 0;
        }
    }

    /** pipeline 批量 SCARD，单次往返 */
    @SuppressWarnings("unchecked")
    private List<Long> pipelineSize(List<String> keys) {
        try {
            List<Object> results = this.redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings("rawtypes")
                public Object execute(RedisOperations operations) {
                    for (String key : keys) {
                        operations.opsForSet().size(key);
                    }
                    return null;
                }
            });
            List<Long> sizes = new ArrayList<>(keys.size());
            for (Object r : results != null ? results : List.of()) {
                sizes.add(r instanceof Number number ? number.longValue() : 0L);
            }
            return sizes;
        } catch (Exception e) {
            LOGGER.warn("日活序列 pipeline 统计失败", e);
            List<Long> zeros = new ArrayList<>(keys.size());
            for (int i = 0; i < keys.size(); i++) {
                zeros.add(0L);
            }
            return zeros;
        }
    }

    // ==================== 审核/举报聚合辅助 ====================

    /** 构造"按天 COUNT(*)"分组查询 wrapper，调用处用类型见证 this.&lt;Review&gt; 指定实体泛型 */
    private <T> QueryWrapper<T> dateGroupWrapper(String dateExpr, String column, Date start) {
        return new QueryWrapper<T>().select(dateExpr + " AS d", "COUNT(*) AS c")
                .ge(column, start).groupBy(dateExpr);
    }

    /** 构造"按天 + 状态 COUNT(*)"分组查询 wrapper */
    private <T> QueryWrapper<T> dateStatusGroupWrapper(String dateExpr, String column, Date start,
                                                       String status1, String status2) {
        return new QueryWrapper<T>().select(dateExpr + " AS d", "status", "COUNT(*) AS c")
                .ge(column, start).in("status", status1, status2)
                .groupBy(dateExpr, "status");
    }

    /** 将按天分组行转成 yyyy-MM-dd → 数量 */
    private Map<String, Long> dateCountMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object d = row.get("d");
                if (d != null) {
                    map.put(String.valueOf(d), toLong(row.get("c")));
                }
            }
        }
        return map;
    }

    /** 将按天+状态分组行转成 yyyy-MM-dd → (状态 → 数量) */
    private Map<String, Map<String, Long>> dateStatusCountMap(List<Map<String, Object>> rows) {
        Map<String, Map<String, Long>> map = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object d = row.get("d");
                if (d != null) {
                    map.computeIfAbsent(String.valueOf(d), k -> new HashMap<>())
                            .put(String.valueOf(row.get("status")), toLong(row.get("c")));
                }
            }
        }
        return map;
    }

    /** 将分组行转成 [{name, count}]，NULL 组归入"未知" */
    private List<Map<String, Object>> groupCount(List<Map<String, Object>> rows, String nameColumn) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (rows != null) {
            // 保持 SQL 排序（多数带 ORDER BY cnt DESC）
            for (Map<String, Object> row : rows) {
                Object name = row.get(nameColumn);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", name != null ? String.valueOf(name) : "UNKNOWN");
                item.put("count", toLong(row.get("cnt")));
                list.add(item);
            }
        }
        return list;
    }

    private static List<String> buildDateLabels(LocalDate today, int days) {
        List<String> labels = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            labels.add(today.minusDays(days - 1L - i).format(MMDD_FMT));
        }
        return labels;
    }

    // ==================== 通用辅助 ====================

    /** 总用户数（tb_user 由 BFF 独占） */
    private long countTotalUsers() {
        Long count = this.userMapper.selectCount(null);
        return count != null ? count : 0L;
    }

    /** RPC 调用降级：领域服务不可用时返回兜底值，看板局部失败不拖垮整页 */
    private <T> T safeRpc(RpcCall<T> call, T fallback) {
        try {
            T value = call.call();
            return value != null ? value : fallback;
        } catch (Exception e) {
            LOGGER.warn("看板 RPC 调用失败，使用兜底值", e);
            return fallback;
        }
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /** 允许 lambda 抛受检异常的 RPC 调用包装 */
    @FunctionalInterface
    private interface RpcCall<T> {
        T call() throws Exception;
    }
}
