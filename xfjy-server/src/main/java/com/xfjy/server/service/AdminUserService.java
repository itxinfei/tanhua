package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfjy.service.api.WuKongImApi;
import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.enums.SexEnum;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.server.dto.AdminProfileUpdateDTO;
import com.xfjy.server.exception.BusinessException;
import com.xfjy.server.mapper.OperationLogMapper;
import com.xfjy.server.mapper.ReportMapper;
import com.xfjy.server.mapper.UserMapper;
import com.xfjy.server.pojo.OperationLog;
import com.xfjy.server.pojo.User;
import com.xfjy.server.utils.OperationLogContext;
import com.xfjy.server.vo.ProfileLogVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private WuKongImApi wuKongImApi;

    @Autowired
    private QuanZiApi quanZiApi;

    @Autowired
    private OperationLogMapper operationLogMapper;

    /**
     * 照片墙 JSON 仅含普通字符串，用线程安全的默认 ObjectMapper 单例即可；
     * 本服务容器未注册 Jackson ObjectMapper bean，故直接实例化复用。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${admin.reset-password:123456}")
    private String resetPassword;

    /**
     * 分页查询用户列表
     */
    public Map<String, Object> queryUserPage(Integer page, Integer pageSize,
                                              String keyword, String gender, String status,
                                              String startDate, String endDate) {
        QueryWrapper<User> userWrapper = buildUserFilterWrapper(keyword, gender, status, startDate, endDate);
        if (userWrapper == null) {
            // 性别/关键词无匹配用户，直接返回空结果
            Map<String, Object> empty = new HashMap<>();
            empty.put("total", 0L);
            empty.put("list", new ArrayList<>());
            return empty;
        }
        userWrapper.orderByDesc("created");

        IPage<User> userPage = this.userMapper.selectPage(new Page<>(page, pageSize), userWrapper);

        // 组装返回数据（合并UserInfo）
        List<Map<String, Object>> items = new ArrayList<>();
        for (User user : userPage.getRecords()) {
            items.add(buildUserItem(user));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", userPage.getTotal());
        result.put("list", items);
        return result;
    }

    /**
     * 导出用户列表（返回全部匹配数据，不分页）
     */
    public List<Map<String, Object>> exportUsers(String keyword, String gender, String status,
                                  String startDate, String endDate) {
        QueryWrapper<User> userWrapper = buildUserFilterWrapper(keyword, gender, status, startDate, endDate);
        if (userWrapper == null) {
            return new ArrayList<>();
        }
        userWrapper.orderByDesc("created");
        List<User> users = this.userMapper.selectList(userWrapper);
        // 合并 UserInfo（昵称/性别/年龄/城市），与分页列表数据结构保持一致
        List<Map<String, Object>> items = new ArrayList<>();
        for (User user : users) {
            items.add(buildUserItem(user));
        }
        return items;
    }

    /**
     * 构建用户过滤条件（分页/导出共用）
     */
    private QueryWrapper<User> buildUserFilterWrapper(String keyword, String gender, String status,
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
                return null; // 返回 null 表示无匹配数据
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
                return null; // 返回 null 表示无匹配数据
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
        return userWrapper;
    }

    /**
     * 构建用户信息 Item（合并UserInfo）
     */
    private Map<String, Object> buildUserItem(User user) {
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
        return item;
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
            item.put("height", userInfo.getHeight());

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
        // updateById 默认忽略 null，封禁原因必须用 UpdateWrapper 显式置空
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, userId)
                .set(User::getStatus, "NORMAL")
                .set(User::getFreezeReason, null);
        return this.userMapper.update(null, wrapper) > 0;
    }

    /**
     * 重置用户密码（重置为123456）
     */
    public Boolean resetPassword(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setPassword(org.apache.commons.codec.digest.DigestUtils.md5Hex(resetPassword));
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
     * 查询用户会话列表（调 IM REST API，补充对方昵称/头像并按最近活跃排序）
     */
    public List<Map<String, Object>> queryChatSessions(Long userId) {
        List<Map<String, Object>> sessions = this.wuKongImApi.getChatSessions(String.valueOf(userId), 20);
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
                // contactId 非数字（如 IM 客服账号），直接作为昵称展示
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
     * 查询与某用户的聊天记录（调 IM REST API）
     */
    public Map<String, Object> queryChatMessages(Long userId, String contactId, Integer pageSize) {
        int limit = (pageSize == null || pageSize <= 0) ? 20 : pageSize;
        return this.wuKongImApi.getChatMessages(String.valueOf(userId), contactId, limit, null);
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

    /**
     * 客服代改用户资料。
     *
     * <p>tb_user_info 的唯一写入方是 user-service，此处只组装白名单字段后走 RPC
     * （MyBatis-Plus 更新默认忽略 null，故只 set 本次提交的字段，实现部分更新）。
     * 变更字段生成「旧值→新值」摘要，通过 {@link OperationLogContext} 交给审计切面。</p>
     *
     * @return true 更新成功；false 表示用户不存在（Controller 据此返回 404）
     */
    public boolean updateUserProfile(Long userId, AdminProfileUpdateDTO dto) {
        User user = this.userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        UserInfo old = this.userInfoApi.queryByUserId(userId);

        UserInfo info = new UserInfo();
        info.setUserId(userId);
        List<String> diffs = new ArrayList<>();

        if (dto.getNickname() != null) {
            String value = dto.getNickname().trim();
            if (value.length() < 2 || value.length() > 20) {
                throw new BusinessException("昵称长度需2-20个字符");
            }
            info.setNickName(value);
            appendDiff(diffs, "昵称", old == null ? null : old.getNickName(), value);
        }
        if (dto.getAvatar() != null) {
            String value = dto.getAvatar().trim();
            info.setLogo(value);
            String oldLogo = old == null ? "" : StringUtils.trimToEmpty(old.getLogo());
            if (!oldLogo.equals(value)) {
                diffs.add(value.isEmpty() ? "头像[已清空]" : "头像[已更换]");
            }
        }
        if (dto.getSignature() != null) {
            String value = dto.getSignature().trim();
            info.setSignature(value);
            appendDiff(diffs, "签名", old == null ? null : old.getSignature(), value);
        }
        if (dto.getGender() != null) {
            SexEnum sex = "man".equalsIgnoreCase(dto.getGender()) ? SexEnum.MAN : SexEnum.WOMAN;
            info.setSex(sex);
            String oldGender = (old == null || old.getSex() == null) ? "" : old.getSex().toString();
            appendDiff(diffs, "性别", oldGender, sex.toString());
        }
        if (dto.getCity() != null) {
            String value = dto.getCity().trim();
            info.setCity(value);
            appendDiff(diffs, "城市", old == null ? null : old.getCity(), value);
        }
        if (dto.getHeight() != null) {
            String value = dto.getHeight().trim();
            info.setHeight(value);
            appendDiff(diffs, "身高", old == null ? null : old.getHeight(), value);
        }
        if (dto.getEducation() != null) {
            String value = dto.getEducation().trim();
            info.setEdu(value);
            appendDiff(diffs, "学历", old == null ? null : old.getEdu(), value);
        }
        if (dto.getProfession() != null) {
            String value = dto.getProfession().trim();
            info.setIndustry(value);
            appendDiff(diffs, "行业", old == null ? null : old.getIndustry(), value);
        }
        if (dto.getIncome() != null) {
            String value = dto.getIncome().trim();
            info.setIncome(value);
            appendDiff(diffs, "收入", old == null ? null : old.getIncome(), value);
        }
        if (dto.getMarriage() != null) {
            String value = dto.getMarriage() == 1 ? "已婚" : "未婚";
            info.setMarriage(value);
            appendDiff(diffs, "婚姻", old == null ? null : old.getMarriage(), value);
        }
        if (dto.getPhotoAlbum() != null) {
            List<String> newPhotos = parsePhotoAlbumInput(dto.getPhotoAlbum());
            List<String> oldPhotos = old == null ? new ArrayList<>() : parsePhotoAlbum(old.getPhotoAlbum());
            info.setPhotoAlbum(writePhotoAlbum(newPhotos));
            if (!oldPhotos.equals(newPhotos)) {
                diffs.add("照片墙[" + oldPhotos.size() + "张→" + newPhotos.size() + "张]");
            }
        }

        if (diffs.isEmpty()) {
            throw new BusinessException("未检测到资料变更");
        }

        Boolean ok = this.userInfoApi.updateUserInfoByUserId(info);
        if (!Boolean.TRUE.equals(ok)) {
            throw new BusinessException("资料更新失败，请稍后重试");
        }
        OperationLogContext.setDetail(String.join("; ", diffs));
        return true;
    }

    /**
     * 分页查询用户资料编辑历史（tb_operation_log 中 module=USER、action=EDIT_PROFILE 的记录）。
     */
    public Map<String, Object> queryProfileLogs(Long userId, Integer page, Integer pageSize) {
        QueryWrapper<OperationLog> wrapper = new QueryWrapper<>();
        wrapper.eq("module", "USER")
                .eq("target_id", String.valueOf(userId))
                .eq("action", "EDIT_PROFILE")
                .orderByDesc("created");
        IPage<OperationLog> logPage =
                this.operationLogMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<ProfileLogVO> list = logPage.getRecords().stream()
                .map(this::toProfileLogVO)
                .collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("total", logPage.getTotal());
        result.put("list", list);
        return result;
    }

    private ProfileLogVO toProfileLogVO(OperationLog log) {
        ProfileLogVO vo = new ProfileLogVO();
        vo.setId(log.getId());
        vo.setOperatorId(log.getAdminId());
        vo.setOperatorName(log.getAdminUsername());
        vo.setDetail(log.getDetail());
        vo.setIp(log.getIp());
        vo.setCreatedAt(log.getCreated());
        return vo;
    }

    /**
     * 追加一条字段变更摘要；旧值与新值（trim 后）相同则不记录。空值统一展示为「空」。
     */
    private void appendDiff(List<String> diffs, String label, String oldValue, String newValue) {
        String oldStr = oldValue == null ? "" : oldValue.trim();
        String newStr = newValue == null ? "" : newValue.trim();
        if (oldStr.equals(newStr)) {
            return;
        }
        diffs.add(label + "[" + displayDiffValue(oldStr) + "→" + displayDiffValue(newStr) + "]");
    }

    private String displayDiffValue(String value) {
        return value.isEmpty() ? "空" : value;
    }

    /**
     * 解析照片墙入参 JSON 数组字符串，校验元素与张数（最多 6 张）。
     * 空串/空白视为清空（空列表）。
     */
    private List<String> parsePhotoAlbumInput(String json) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        List<?> raw;
        try {
            raw = this.objectMapper.readValue(json, new TypeReference<List<?>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("照片墙数据格式非法");
        }
        List<String> urls = new ArrayList<>();
        for (Object item : raw) {
            if (item == null) {
                continue;
            }
            String url = String.valueOf(item).trim();
            if (!url.isEmpty()) {
                urls.add(url);
            }
        }
        if (urls.size() > 6) {
            throw new BusinessException("照片墙最多上传6张图片");
        }
        return urls;
    }

    /**
     * 照片墙列表序列化为规范 JSON 数组字符串后落库。
     */
    private String writePhotoAlbum(List<String> photos) {
        try {
            return this.objectMapper.writeValueAsString(photos);
        } catch (Exception e) {
            // 元素均为普通字符串，序列化不会失败；兜底按清空处理并告警
            log.warn("照片墙序列化失败, photos={}", photos, e);
            return "[]";
        }
    }
}