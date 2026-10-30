package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.service.api.HuanXinApi;
import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.server.mapper.ReportMapper;
import com.xfjy.server.mapper.UserMapper;
import com.xfjy.server.pojo.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台用户管理服务
 */
@Slf4j
@Service
public class AdminUserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserInfoApi userInfoApi;

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private HuanXinApi huanXinApi;

    @Autowired
    private QuanZiApi quanZiApi;

    /**
     * 分页查询用户列表
     */
    public Map<String, Object> queryUserPage(Integer page, Integer pageSize,
                                              String keyword, String gender, String status,
                                              String startDate, String endDate) {
        QueryWrapper<User> userWrapper = new QueryWrapper<>();
        // 根据性别筛选：先查出符合条件的用户ID集合
        if (StringUtils.isNotBlank(gender)) {
            Integer sexValue = "男".equals(gender) ? 1 : 2; //SexEnum: MAN=1, WOMAN=2
            UserInfoQueryParam genderParam = new UserInfoQueryParam();
            genderParam.setSex(sexValue);
            List<UserInfo> infoList = this.userInfoApi.queryUserInfoList(genderParam);
            List<Long> userIds = infoList.stream().map(UserInfo::getUserId).collect(java.util.stream.Collectors.toList());
            if (userIds.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("total", 0L);
                empty.put("list", new ArrayList<>());
                return empty;
            }
            userWrapper.in("id", userIds);
        }

        // 关键词搜索：手机号（MySQL）+ 昵称（UserInfo）取并集
        if (StringUtils.isNotBlank(keyword)) {
            List<Long> keywordUserIds = new ArrayList<>();

            // 1. 手机号模糊匹配
            QueryWrapper<User> mobileWrapper = new QueryWrapper<>();
            mobileWrapper.like("mobile", keyword).select("id");
            List<User> mobileMatches = this.userMapper.selectList(mobileWrapper);
            mobileMatches.forEach(u -> keywordUserIds.add(u.getId()));

            // 2. 昵称模糊匹配
            UserInfoQueryParam nickParam = new UserInfoQueryParam();
            nickParam.setNickName(keyword);
            List<UserInfo> nickMatches = this.userInfoApi.queryUserInfoList(nickParam);
            nickMatches.forEach(info -> {
                if (!keywordUserIds.contains(info.getUserId())) {
                    keywordUserIds.add(info.getUserId());
                }
            });

            if (keywordUserIds.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("total", 0L);
                empty.put("list", new ArrayList<>());
                return empty;
            }
            userWrapper.in("id", keywordUserIds);
        }

        // 查询用户基本信息
        if (StringUtils.isNotBlank(status)) {
            userWrapper.eq("status", status);
        }
        if (StringUtils.isNotBlank(startDate)) {
            userWrapper.ge("created", parseDateToTimestamp(startDate));
        }
        if (StringUtils.isNotBlank(endDate)) {
            userWrapper.le("created", parseDateToTimestamp(endDate) + 86399999L); // 包含当天
        }
        userWrapper.orderByDesc("created");

        IPage<User> userPage = this.userMapper.selectPage(new Page<>(page, pageSize), userWrapper);

        // 组装返回数据（合并UserInfo）
        List<Map<String, Object>> items = new ArrayList<>();
        for (User user : userPage.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("mobile", user.getMobile());
            item.put("status", user.getStatus() != null ? user.getStatus() : "NORMAL");
            item.put("freezeReason", user.getFreezeReason());
            item.put("createdAt", user.getCreated());
            item.put("lastActiveAt", user.getUpdated());

            // 查询用户详情
            UserInfo userInfo = this.userInfoApi.queryByUserId(user.getId());
            if (userInfo != null) {
                item.put("nickname", userInfo.getNickName());
                item.put("avatar", userInfo.getLogo());
                item.put("gender", userInfo.getSex() != null ? userInfo.getSex().toString() : "未知");
                item.put("age", userInfo.getAge());
                item.put("city", userInfo.getCity());
                item.put("vipLevel", 0); //VIP功能待实现
            } else {
                item.put("nickname", "用户" + user.getId());
                item.put("avatar", "");
                item.put("gender", "未知");
                item.put("age", 0);
                item.put("city", "");
                item.put("vipLevel", 0);
            }
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", userPage.getTotal());
        result.put("list", items);
        return result;
    }

    /**
     * 查询用户详情（增强版：补充照片墙、标签、计数等字段）
     */
    public Map<String, Object> queryUserDetail(Long userId) {
        User user = this.userMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        Map<String, Object> item = new HashMap<>();
        item.put("id", user.getId());
        item.put("mobile", user.getMobile());
        item.put("status", user.getStatus() != null ? user.getStatus() : "NORMAL");
        item.put("freezeReason", user.getFreezeReason());
        item.put("createdAt", user.getCreated());
        item.put("lastActiveAt", user.getUpdated());

        UserInfo userInfo = this.userInfoApi.queryByUserId(userId);
        if (userInfo != null) {
            item.put("nickname", userInfo.getNickName());
            item.put("avatar", userInfo.getLogo());
            item.put("gender", userInfo.getSex() != null ? userInfo.getSex().toString() : "未知");
            item.put("age", userInfo.getAge());
            item.put("city", userInfo.getCity());
            item.put("coverPic", userInfo.getCoverPic());
            item.put("edu", userInfo.getEdu());
            item.put("birthday", userInfo.getBirthday());
            item.put("industry", userInfo.getIndustry());
            item.put("income", userInfo.getIncome());
            item.put("marriage", userInfo.getMarriage());

            // 照片墙：photoAlbum 是 JSON 数组字符串 ["url1","url2"]
            item.put("photos", parsePhotoAlbum(userInfo.getPhotoAlbum()));

            // 标签：tags 是逗号分隔字符串 "高学历,有趣,爱运动"
            List<String> tags = new ArrayList<>();
            if (StringUtils.isNotBlank(userInfo.getTags())) {
                for (String tag : userInfo.getTags().split(",")) {
                    String trimmed = tag.trim();
                    if (!trimmed.isEmpty()) tags.add(trimmed);
                }
            }
            item.put("tags", tags);
            item.put("signature", userInfo.getSignature());
        } else {
            item.put("nickname", "用户" + userId);
            item.put("avatar", "");
            item.put("gender", "未知");
            item.put("age", 0);
            item.put("city", "");
            item.put("photos", Collections.emptyList());
            item.put("tags", Collections.emptyList());
            item.put("signature", "");
        }

        // 被举报数：查 MySQL tb_report
        QueryWrapper<com.xfjy.server.pojo.Report> reportWrapper = new QueryWrapper<>();
        reportWrapper.eq("target_user_id", userId);
        item.put("reportCount", this.reportMapper.selectCount(reportWrapper));

        // 动态数/粉丝数/关注数/匹配数：调 circle-service 计数 API（MongoDB）
        try {
            item.put("postCount", this.quanZiApi.countPublishByUser(userId));
            item.put("followerCount", this.quanZiApi.countFollowerByUser(userId));
            item.put("followingCount", this.quanZiApi.countFollowingByUser(userId));
            item.put("matchCount", this.quanZiApi.countLikeByUser(userId));
        } catch (Exception e) {
            // circle-service 不可用时降级为 0，不阻断详情页加载
            log.warn("[AdminUser] circle-service 计数查询失败, userId={}, 降级为0", userId, e);
            item.put("postCount", 0L);
            item.put("followerCount", 0L);
            item.put("followingCount", 0L);
            item.put("matchCount", 0L);
        }
        item.put("vipLevel", 0); // VIP功能待实现

        return item;
    }

    /**
     * 解析 photoAlbum JSON 数组字符串为 List
     */
    private List<String> parsePhotoAlbum(String photoAlbum) {
        List<String> photos = new ArrayList<>();
        if (StringUtils.isBlank(photoAlbum) || !photoAlbum.startsWith("[")) {
            return photos;
        }
        String inner = photoAlbum.substring(1, photoAlbum.length() - 1);
        for (String url : inner.split(",")) {
            String trimmed = url.trim().replace("\"", "");
            if (!trimmed.isEmpty()) photos.add(trimmed);
        }
        return photos;
    }

    /**
     * 封禁用户
     */
    public Boolean freezeUser(Long userId, String reason) {
        User user = new User();
        user.setId(userId);
        user.setStatus("FROZEN");
        user.setFreezeReason(reason);
        return this.userMapper.updateById(user) > 0;
    }

    /**
     * 解封用户
     */
    public Boolean unfreezeUser(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setStatus("NORMAL");
        user.setFreezeReason(null);
        return this.userMapper.updateById(user) > 0;
    }

    /**
     * 重置用户密码（重置为123456）
     */
    public Boolean resetPassword(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setPassword(org.apache.commons.codec.digest.DigestUtils.md5Hex("123456"));
        return this.userMapper.updateById(user) > 0;
    }

    /**
     * 批量封禁用户
     *
     * @param userIds 用户ID列表
     * @param reason  封禁原因
     * @return {success: n, failed: m}
     */
    public Map<String, Object> batchFreezeUser(List<Long> userIds, String reason) {
        if (userIds == null || userIds.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("success", 0);
            empty.put("failed", 0);
            return empty;
        }

        int success = 0;
        int failed = 0;
        for (Long userId : userIds) {
            try {
                if (freezeUser(userId, reason)) {
                    success++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                log.error("批量封禁用户失败 userId={}", userId, e);
                failed++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        log.info("批量封禁用户完成: success={}, failed={}", success, failed);
        return result;
    }

    /**
     * 批量解封用户
     *
     * @param userIds 用户ID列表
     * @return {success: n, failed: m}
     */
    public Map<String, Object> batchUnfreezeUser(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("success", 0);
            empty.put("failed", 0);
            return empty;
        }

        int success = 0;
        int failed = 0;
        for (Long userId : userIds) {
            try {
                if (unfreezeUser(userId)) {
                    success++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                log.error("批量解封用户失败 userId={}", userId, e);
                failed++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        log.info("批量解封用户完成: success={}, failed={}", success, failed);
        return result;
    }

    /**
     * 查询用户会话列表（调环信 REST API，补充对方昵称/头像并按最近活跃排序）
     */
    public List<Map<String, Object>> queryChatSessions(Long userId) {
        List<Map<String, Object>> sessions = this.huanXinApi.getChatSessions(String.valueOf(userId), 20);
        // 补充对方昵称/头像（contactId 即对方 userId）
        for (Map<String, Object> session : sessions) {
            String contactId = String.valueOf(session.get("contactId"));
            try {
                UserInfo contactInfo = this.userInfoApi.queryByUserId(Long.parseLong(contactId));
                if (contactInfo != null && StringUtils.isNotBlank(contactInfo.getNickName())) {
                    session.put("nickname", contactInfo.getNickName());
                } else {
                    session.put("nickname", "用户" + contactId);
                }
                session.put("avatar", contactInfo != null ? contactInfo.getLogo() : "");
            } catch (NumberFormatException e) {
                // contactId 非数字（如环信客服账号），直接作为昵称展示
                session.put("nickname", contactId);
                session.put("avatar", "");
            } catch (Exception e) {
                // user-service 不可用时降级，不阻断会话列表
                log.warn("[AdminUser] 查询联系人信息失败 contactId={}", contactId, e);
                session.put("nickname", "用户" + contactId);
                session.put("avatar", "");
            }
        }
        // 按最后消息时间降序（最近活跃在前）
        sessions.sort((a, b) -> {
            long aTime = a.get("lastTime") instanceof Number ? ((Number) a.get("lastTime")).longValue() : 0L;
            long bTime = b.get("lastTime") instanceof Number ? ((Number) b.get("lastTime")).longValue() : 0L;
            return Long.compare(bTime, aTime);
        });
        return sessions;
    }

    /**
     * 查询与某用户的聊天记录（调环信 REST API）
     */
    public Map<String, Object> queryChatMessages(Long userId, String contactId, Integer pageSize) {
        int limit = (pageSize == null || pageSize <= 0) ? 20 : pageSize;
        return this.huanXinApi.getChatMessages(String.valueOf(userId), contactId, limit, null);
    }

    /**
     * 将 yyyy-MM-dd 日期字符串转为当天 00:00:00 的时间戳（毫秒）
     * 解析失败返回 0，调用方应判空跳过
     */
    private long parseDateToTimestamp(String dateStr) {
        try {
            return java.sql.Date.valueOf(dateStr).getTime();
        } catch (Exception e) {
            log.warn("日期解析失败: {}", dateStr);
            return 0L;
        }
    }
}