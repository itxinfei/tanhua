package com.xfjy.service.api;

import com.xfjy.service.api.SwipeLogApi;
import com.xfjy.user.mapper.SwipeLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 滑动日志服务实现（xfjy-user-service 侧；由 BFF 层通过 @HttpExchange 代理调用）
 */
@Slf4j
@RestController
@RequestMapping("/api/swipe-log")
public class SwipeLogApiImpl implements SwipeLogApi {

    /** 看板趋势日期格式（MM-dd），与 BFF 看板其它接口保持一致 */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");

    @Autowired
    private SwipeLogMapper swipeLogMapper;

    @Override
    @PostMapping("/record")
    public Boolean recordSwipe(@RequestParam("userId") Long userId,
                               @RequestParam("targetId") Long targetId,
                               @RequestParam("action") String action,
                               @RequestParam(value = "matched", defaultValue = "0") Integer matched) {
        try {
            int rows = this.swipeLogMapper.recordSwipe(userId, targetId, action, matched != null ? matched : 0);
            // rows=0 时（幂等跳过）仍视为成功，不阻断主链路
            return true;
        } catch (Exception e) {
            // 滑动日志写入失败不得阻断主链路（旁路：仅打日志）
            log.warn("滑动日志写入失败：userId={}, targetId={}, action={}", userId, targetId, action, e);
            return false;
        }
    }

    @Override
    @GetMapping("/count-today")
    public Long countTodaySwipes(@RequestParam("userId") Long userId) {
        try {
            Long count = this.swipeLogMapper.countTodaySwipes(userId);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("查询今日滑动次数失败：userId={}", userId, e);
            // 数据库异常时返回 0（让请求继续走主链路 + 权益兜底拦截）
            return 0L;
        }
    }

    @Override
    @GetMapping("/admin/daily-stats/today")
    public Map<String, Object> adminTodayFunnel() {
        Map<String, Object> row = this.swipeLogMapper.selectTodayFunnel();
        Map<String, Object> result = new HashMap<>();
        if (row == null) {
            row = Map.of();
        }
        result.put("swipes", toLong(row.get("swipes")));
        result.put("likes", toLong(row.get("likes")));
        result.put("superlikes", toLong(row.get("superlikes")));
        result.put("matches", toLong(row.get("matches")));
        result.put("likeUsers", toLong(row.get("likeUsers")));
        result.put("matchedUsers", toLong(row.get("matchedUsers")));
        return result;
    }

    @Override
    @GetMapping("/admin/daily-stats")
    public Map<String, Object> adminDailyStats(@RequestParam("days") Integer days) {
        if (days == null || days <= 0) {
            days = 7;
        }
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1L);
        List<Map<String, Object>> rows = this.swipeLogMapper.selectDailyStats(startDate.atStartOfDay());

        // DB 日期（LocalDate/java.sql.Date 的 toString 均为 yyyy-MM-dd）→ 指标行，缺日补零
        Map<String, Map<String, Object>> rowByDate = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object d = row.get("d");
                if (d != null) {
                    rowByDate.put(String.valueOf(d), row);
                }
            }
        }

        List<String> dates = new ArrayList<>(days);
        List<Long> swipes = new ArrayList<>(days);
        List<Long> likes = new ArrayList<>(days);
        List<Long> superlikes = new ArrayList<>(days);
        List<Long> matches = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate day = startDate.plusDays(i);
            dates.add(day.format(DATE_FMT));
            Map<String, Object> row = rowByDate.get(day.toString());
            swipes.add(row == null ? 0L : toLong(row.get("swipes")));
            likes.add(row == null ? 0L : toLong(row.get("likes")));
            superlikes.add(row == null ? 0L : toLong(row.get("superlikes")));
            matches.add(row == null ? 0L : toLong(row.get("matches")));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("swipes", swipes);
        result.put("likes", likes);
        result.put("superlikes", superlikes);
        result.put("matches", matches);
        return result;
    }

    /** SUM/COUNT 聚合列在不同 MySQL 驱动版本下可能返回 Long/BigInteger/BigDecimal，统一转 long */
    private static long toLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
