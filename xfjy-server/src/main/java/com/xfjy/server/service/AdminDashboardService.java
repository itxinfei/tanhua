package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.server.mapper.ReportMapper;
import com.xfjy.server.mapper.ReviewMapper;
import com.xfjy.server.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
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

        // DAU / 匹配率 / 付费率 / 动态总数：暂无真实统计源，返回 null 由前端显示"待接入"，
        // 严禁用总用户数或 0 冒充真实指标误导运营决策
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
        List<Integer> dauList = new ArrayList<>();
        List<Integer> matchRateList = new ArrayList<>();
        List<Integer> payRateList = new ArrayList<>();

        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -(days - 1));

        for (int i = 0; i < days; i++) {
            Date dayStart = cal.getTime();
            Calendar endCal = (Calendar) cal.clone();
            endCal.add(Calendar.DAY_OF_MONTH, 1);
            Date dayEnd = endCal.getTime();

            dates.add(sdf.format(dayStart));

            // 当日新增用户
            QueryWrapper<com.xfjy.server.pojo.User> dayWrapper = new QueryWrapper<>();
            dayWrapper.ge("created", dayStart).lt("created", dayEnd);
            newUserList.add(this.userMapper.selectCount(dayWrapper));

            // DAU等指标暂无统计，填充0
            dauList.add(0);
            matchRateList.add(0);
            payRateList.add(0);

            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("dauList", dauList);
        result.put("newUserList", newUserList);
        result.put("matchRateList", matchRateList);
        result.put("payRateList", payRateList);
        return result;
    }

    /**
     * 性别比例
     */
    public Map<String, Object> genderRatio() {
        UserInfoQueryParam maleParam = new UserInfoQueryParam();
        maleParam.setSex(1);
        Long male = (long) this.userInfoApi.queryUserInfoList(maleParam).size();
        UserInfoQueryParam femaleParam = new UserInfoQueryParam();
        femaleParam.setSex(2);
        Long female = (long) this.userInfoApi.queryUserInfoList(femaleParam).size();

        Map<String, Object> result = new HashMap<>();
        result.put("male", male);
        result.put("female", female);
        return result;
    }

    /**
     * 年龄分布
     */
    public Map<String, Object> ageDistribution() {
        List<UserInfo> allUsers = this.userInfoApi.queryUserInfoList(new UserInfoQueryParam());

        // 分段统计
        int[] buckets = new int[6]; //18以下/18-24/25-30/31-40/41-50/50以上
        for (UserInfo info : allUsers) {
            Integer age = info.getAge();
            if (age == null || age <= 0) {
                continue;
            }
            if (age < 18) buckets[0]++;
            else if (age <= 24) buckets[1]++;
            else if (age <= 30) buckets[2]++;
            else if (age <= 40) buckets[3]++;
            else if (age <= 50) buckets[4]++;
            else buckets[5]++;
        }

        List<String> ranges = Arrays.asList("18岁以下", "18-24岁", "25-30岁", "31-40岁", "41-50岁", "50岁以上");
        List<Integer> counts = new ArrayList<>();
        for (int b : buckets) {
            counts.add(b);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("ranges", ranges);
        result.put("counts", counts);
        return result;
    }
}