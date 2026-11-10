package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.service.api.WuKongImApi;
import com.xfjy.service.api.PayApi;
import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.api.VideoApi;
import com.xfjy.service.api.WuKongImApi;
import com.xfjy.service.pojo.Publish;
import com.xfjy.service.pojo.Video;
import com.xfjy.service.vo.PageInfo;
import com.xfjy.server.mapper.ReportMapper;
import com.xfjy.server.mapper.ReviewMapper;
import com.xfjy.server.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 后台数据看板服务
 */
@Service
public class AdminDashboardService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserInfoApi userInfoApi;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private PayApi payApi;

    @Autowired
    private QuanZiApi quanZiApi;

    @Autowired
    private VideoApi videoApi;

    @Autowired
    private WuKongImApi wuKongImApi;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /** 在线用户 ZSET key，与 TokenInterceptor 保持一致 */
    private static final String ONLINE_KEY = "XFJY_ONLINE_USERS";

    /** 在线用户心跳有效期：5 分钟无请求视为离线 */
    private static final long ONLINE_TTL_MS = 5 * 60 * 1000L;

    /**
     * 看板概览统计
     */
    public Map<String, Object> overview() {
        Map<String, Object> result = new HashMap<>();

        // 总用户数
        Long totalUsers = this.userMapper.selectCount(null);
        result.put("totalUsers", totalUsers);

        // 今日新增用户
        Date todayStart = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        QueryWrapper<com.xfjy.server.pojo.User> todayWrapper = new QueryWrapper<>();
        todayWrapper.ge("created", todayStart);
        Long newUsers = this.userMapper.selectCount(todayWrapper);
        result.put("newUsers", newUsers);
        result.put("newUsersChange", null);

        // DAU / 匹配率 / 付费率 / 动态总数：暂无真实统计源，返回 null 由前端显示"待接入"
        result.put("dau", null);
        result.put("dauChange", null);
        result.put("matchRate", null);
        result.put("matchRateChange", null);
        result.put("payRate", null);
        result.put("payRateChange", null);
        result.put("totalPosts", null);

        // 待处理举报数
        QueryWrapper<com.xfjy.server.pojo.Report> reportWrapper = new QueryWrapper<>();
        reportWrapper.eq("status", "PENDING");
        Long totalReports = this.reportMapper.selectCount(reportWrapper);
        result.put("totalReports", totalReports);

        // 待审核数
        QueryWrapper<com.xfjy.server.pojo.Review> reviewWrapper = new QueryWrapper<>();
        reviewWrapper.eq("status", "PENDING");
        Long pendingReviews = this.reviewMapper.selectCount(reviewWrapper);
        result.put("pendingReviews", pendingReviews);

        return result;
    }

    /**
     * 趋势数据（近N天）
     */
    public Map<String, Object> trend(Integer days) {
        if (days == null || days <= 0) {
            days = 7;
        }

        List<String> dates = new ArrayList<>();
        List<Long> newUserList = new ArrayList<>();

        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("MM-dd");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -(days - 1));

        for (int i = 0; i < days; i++) {
            Date dayStart = cal.getTime();
            Calendar endCal = (Calendar) cal.clone();
            endCal.add(Calendar.DAY_OF_MONTH, 1);
            Date dayEnd = endCal.getTime();

            dates.add(sdf.format(dayStart.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()));

            // 当日新增用户
            QueryWrapper<com.xfjy.server.pojo.User> dayWrapper = new QueryWrapper<>();
            dayWrapper.ge("created", dayStart).lt("created", dayEnd);
            newUserList.add(this.userMapper.selectCount(dayWrapper));

            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("dauList", null);
        result.put("newUserList", newUserList);
        result.put("matchRateList", null);
        result.put("payRateList", null);
        return result;
    }

    /**
     * 性别比例
     *
     * 走 user-service 的 SQL 聚合（/admin/demographics），不再把 tb_user_info 全表拉回 BFF 内存计数。
     */
    public Map<String, Object> genderRatio() {
        Map<String, Object> demographics = this.userInfoApi.adminDemographics();
        Map<String, Object> result = new HashMap<>();
        result.put("male", toLong(demographics.get("male")));
        result.put("female", toLong(demographics.get("female")));
        return result;
    }

    /**
     * 年龄分布
     *
     * 同上：分段口径由 user-service 在 SQL 里 CASE WHEN 聚合，BFF 只做字段映射。
     * 分段口径与原实现保持一致：age 为 NULL 或 <=0 不计入，18/24/30/40/50 边界值落在低一档。
     */
    public Map<String, Object> ageDistribution() {
        Map<String, Object> demographics = this.userInfoApi.adminDemographics();

        Object ranges = demographics.get("ageRanges");
        Object counts = demographics.get("ageCounts");

        Map<String, Object> result = new HashMap<>();
        result.put("ranges", ranges != null ? ranges
                : Arrays.asList("18岁以下", "18-24岁", "25-30岁", "31-40岁", "41-50岁", "50岁以上"));
        result.put("counts", counts != null ? counts : Collections.nCopies(6, 0L));
        return result;
    }

    /** 聚合列在不同驱动版本下可能返回 Long/BigInteger/BigDecimal，统一转 long */
    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * 支付订单趋势（近N天已支付订单数）
     */
    public Map<String, Object> payTrend(Integer days) {
        return this.payApi.payTrend(days);
    }

    /**
     * 支付方式分布（已支付订单按支付方式分组）
     */
    public Map<String, Object> payTypeDistribution() {
        return this.payApi.payTypeDistribution();
    }

    /**
     * 实时统计：今日消息数、今日动态发布数、今日视频数
     *
     * 消息数通过 WuKongImApi 获取（不可用时返回 null）；
     * 动态数和视频数通过 QuanZiApi/VideoApi 的日期范围查询统计
     */
    public Map<String, Object> realtimeStats() {
        Map<String, Object> result = new HashMap<>();

        // 今日消息数（WuKongImApi，不可用时保持 null）
        Long todayMessages = null;
        try {
            todayMessages = this.wuKongImApi.getTodayMessageCount();
        } catch (Exception e) {
            // WuKongImApi 不可用时降级，保持 null
        }
        result.put("todayMessages", todayMessages);

        // 今日动态发布数（QuanZiApi 日期范围查询）
        long todayPosters = 0;
        try {
            Date todayStart = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date tomorrowStart = Date.from(LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            long startTime = todayStart.getTime();
            long endTime = tomorrowStart.getTime();
            PageInfo<Publish> publishPage = this.quanZiApi.queryAllPublishList(1, 1, null, null, startTime, endTime);
            todayPosters = publishPage.getTotal() != null ? publishPage.getTotal() : 0;
        } catch (Exception e) {
            // RPC 不可用时保持 0
        }
        result.put("todayPosters", todayPosters);

        // 今日视频发布数（VideoApi 日期范围查询）
        long todayVideos = 0;
        try {
            Date todayStart = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date tomorrowStart = Date.from(LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            long startTime = todayStart.getTime();
            long endTime = tomorrowStart.getTime();
            PageInfo<Video> videoPage = this.videoApi.queryAllVideoList(1, 1, null, null, startTime, endTime);
            todayVideos = videoPage.getTotal() != null ? videoPage.getTotal() : 0;
        } catch (Exception e) {
            // RPC 不可用时保持 0
        }
        result.put("todayVideos", todayVideos);

        return result;
    }

    /**
     * 在线用户统计：当前在线用户数、今日峰值
     *
     * 通过 Redis ZSET 统计 5 分钟内有请求的用户数；
     * 峰值通过 key `XFJY_PEAK_yyyyMMdd` 记录当日最大在线数
     */
    public Map<String, Object> onlineStats() {
        Map<String, Object> result = new HashMap<>();

        long onlineUsers = 0;
        try {
            long cutoff = System.currentTimeMillis() - ONLINE_TTL_MS;
            Set<String> onlineMembers = this.redisTemplate.opsForZSet().rangeByScore(ONLINE_KEY, cutoff, Double.MAX_VALUE);
            onlineUsers = onlineMembers != null ? onlineMembers.size() : 0;
        } catch (Exception e) {
            // Redis 不可用时返回 0
        }
        result.put("onlineUsers", onlineUsers);

        // 峰值：取当日已记录的最大值（key: XFJY_PEAK_yyyyMMdd）
        long peakToday = onlineUsers;
        try {
            String peakKey = "XFJY_PEAK_" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            String peakVal = this.redisTemplate.opsForValue().get(peakKey);
            if (peakVal != null) {
                peakToday = Math.max(peakToday, Long.parseLong(peakVal));
            }
            // 当前在线数超过历史峰值时更新
            if (onlineUsers > peakToday) {
                this.redisTemplate.opsForValue().set(peakKey, String.valueOf(onlineUsers));
            }
        } catch (Exception e) {
            // Redis 异常时保持当前在线数作为峰值
        }
        result.put("peakToday", peakToday);

        return result;
    }

    /**
     * 热门内容：动态 TOP5 + 视频 TOP5
     *
     * 动态按评论数排序（Publish pojo 无点赞/喜欢字段，通过 queryCommentCountBatch 获取评论数）；
     * 视频按创建时间倒序取最近 5 条（Video pojo 无互动量字段）
     */
    public Map<String, Object> hotContent() {
        Map<String, Object> result = new HashMap<>();

        // 热门动态 TOP5（取最近100条，按评论数排序取前5）
        List<Map<String, Object>> hotPosts = new ArrayList<>();
        try {
            PageInfo<Publish> publishPage = this.quanZiApi.queryAllPublishList(1, 100, null, null, null, null);
            if (publishPage.getRecords() != null && !publishPage.getRecords().isEmpty()) {
                List<Publish> records = publishPage.getRecords();

                // 批量查询点赞数（type=1 点赞，type=2 评论，type=3 喜欢）
                List<String> publishIds = records.stream()
                        .map(p -> p.getId() != null ? p.getId().toHexString() : null)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toList());

                Map<String, Long> likes;
                Map<String, Long> comments;
                try {
                    likes = this.quanZiApi.queryCommentCountBatch(publishIds, 1);
                    comments = this.quanZiApi.queryCommentCountBatch(publishIds, 2);
                } catch (Exception e) {
                    // 评论数查询失败不影响主流程
                    likes = new HashMap<>();
                    comments = new HashMap<>();
                }
                // RPC 降级可能返回 null，统一兜底为空 Map
                if (likes == null) likes = new HashMap<>();
                if (comments == null) comments = new HashMap<>();
                final Map<String, Long> likeCounts = likes;
                final Map<String, Long> commentCounts = comments;

                // 按互动量（点赞+评论）排序
                records.sort((a, b) -> {
                    String idA = a.getId() != null ? a.getId().toHexString() : "";
                    String idB = b.getId() != null ? b.getId().toHexString() : "";
                    long scoreA = likeCounts.getOrDefault(idA, 0L) + commentCounts.getOrDefault(idA, 0L);
                    long scoreB = likeCounts.getOrDefault(idB, 0L) + commentCounts.getOrDefault(idB, 0L);
                    return Long.compare(scoreB, scoreA);
                });

                // 取前5
                for (int i = 0; i < Math.min(5, records.size()); i++) {
                    Publish p = records.get(i);
                    String pid = p.getId() != null ? p.getId().toHexString() : "";
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", pid);
                    item.put("text", p.getText() != null && p.getText().length() > 50
                            ? p.getText().substring(0, 50) + "..." : p.getText());
                    item.put("userId", p.getUserId());
                    item.put("likeCount", likeCounts.getOrDefault(pid, 0L));
                    item.put("commentCount", commentCounts.getOrDefault(pid, 0L));
                    hotPosts.add(item);
                }
            }
        } catch (Exception e) {
            // RPC 异常时返回空列表
        }
        result.put("hotPosts", hotPosts);

        // 热门视频 TOP5（取最近50条，按创建时间倒序取前5）
        List<Map<String, Object>> hotVideos = new ArrayList<>();
        try {
            PageInfo<Video> videoPage = this.videoApi.queryAllVideoList(1, 50, null, null, null, null);
            if (videoPage.getRecords() != null && !videoPage.getRecords().isEmpty()) {
                List<Video> records = videoPage.getRecords();
                for (int i = 0; i < Math.min(5, records.size()); i++) {
                    Video v = records.get(i);
                    Map<String, Object> item = new HashMap<>();
                    item.put("videoId", v.getId() != null ? v.getId().toHexString() : "");
                    item.put("userId", v.getUserId());
                    item.put("text", v.getText() != null && v.getText().length() > 50
                            ? v.getText().substring(0, 50) + "..." : v.getText());
                    hotVideos.add(item);
                }
            }
        } catch (Exception e) {
            // RPC 异常时返回空列表
        }
        result.put("hotVideos", hotVideos);

        return result;
    }

    /**
     * 按维度趋势数据：动态发布趋势 + 视频发布趋势（近N天）
     */
    public Map<String, Object> trendByDimension(Integer days) {
        if (days == null || days <= 0) {
            days = 7;
        }

        List<String> dates = new ArrayList<>();
        List<Long> postList = new ArrayList<>();
        List<Long> videoList = new ArrayList<>();

        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("MM-dd");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -(days - 1));

        for (int i = 0; i < days; i++) {
            Date dayStart = cal.getTime();
            Calendar endCal = (Calendar) cal.clone();
            endCal.add(Calendar.DAY_OF_MONTH, 1);
            Date dayEnd = endCal.getTime();

            dates.add(sdf.format(dayStart.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()));

            // 当日动态发布数
            long postCount = 0;
            try {
                long startTime = dayStart.getTime();
                long endTime = dayEnd.getTime();
                PageInfo<Publish> publishPage = this.quanZiApi.queryAllPublishList(1, 1, null, null, startTime, endTime);
                postCount = publishPage.getTotal() != null ? publishPage.getTotal() : 0;
            } catch (Exception e) {
                // RPC 异常时保持 0
            }
            postList.add(postCount);

            // 当日视频发布数
            long videoCount = 0;
            try {
                long startTime = dayStart.getTime();
                long endTime = dayEnd.getTime();
                PageInfo<Video> videoPage = this.videoApi.queryAllVideoList(1, 1, null, null, startTime, endTime);
                videoCount = videoPage.getTotal() != null ? videoPage.getTotal() : 0;
            } catch (Exception e) {
                // RPC 异常时保持 0
            }
            videoList.add(videoCount);

            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("postList", postList);
        result.put("videoList", videoList);
        return result;
    }
}
