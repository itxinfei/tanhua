package com.xfjy.server.service;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.api.UserLikeApi;
import com.xfjy.service.api.VisitorsApi;
import com.xfjy.service.api.UserApi;
import com.xfjy.service.enums.SexEnum;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.pojo.UserLike;
import com.xfjy.service.pojo.Visitors;
import com.xfjy.service.vo.PageInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.server.mapper.ReportMapper;
import com.xfjy.server.mapper.UserDeviceMapper;
import com.xfjy.server.mapper.UserMapper;
import com.xfjy.server.pojo.*;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.server.vo.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UsersService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsersService.class);

    @Autowired
    private UserInfoApi userInfoApi;

    @Autowired
    private UserLikeApi userLikeApi;

    @Autowired
    private VisitorsApi visitorsApi;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private RecommendUserService recommendUserService;

    @Autowired
    private UserApi userApi;

    @Autowired
    private QuestionService questionService;

    @Autowired
    private BlackListService blackListService;

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private UserDeviceMapper userDeviceMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 短信验证码 Redis key 前缀（与 xfjy-sso 保持一致） */
    private static final String CODE_KEY_PREFIX = "XFJY_SSO_CODE_";
    /**
     * 测试账号固定验证码：仅 user 表 ID=1 可用此码注销，其他账号必须使用真实短信验证码。
     */
    private static final String TEST_USER_FIXED_CODE = "123456";
    /** 测试账号用户ID（与 xfjy-sso 保持一致） */
    private static final Long TEST_USER_ID = 1L;

    public UserInfoVo queryUserInfo(String userID, String huanxinID) {
        User user = UserThreadLocal.get();
        Long userId = user.getId();
        if (StringUtils.isNotBlank(userID)) {
            userId = Long.valueOf(userID);
        } else if (StringUtils.isNotBlank(huanxinID)) {
            userId = Long.valueOf(huanxinID);
        }

        UserInfo userInfo = this.userInfoApi.queryUserInfoById(userId);
        if (null == userInfo) {
            // 兜底：正常路径 user-service 已懒初始化资料行；仅在其不可用等极端场景返回空壳。
            // "资料未完善"是正常业务态，不能让 controller 误判为 500（前端按空字段引导完善资料）
            UserInfoVo emptyVo = new UserInfoVo();
            emptyVo.setId(userId);
            emptyVo.setGender("");
            return emptyVo;
        }

        UserInfoVo userInfoVo = new UserInfoVo();
        userInfoVo.setAge(userInfo.getAge() != null ? userInfo.getAge().toString() : null);
        userInfoVo.setAvatar(userInfo.getLogo());
        userInfoVo.setBirthday(userInfo.getBirthday());
        userInfoVo.setEducation(userInfo.getEdu());
        userInfoVo.setCity(userInfo.getCity());
        SexEnum sex = userInfo.getSex();
        userInfoVo.setGender(sex != null ? sex.name().toLowerCase() : "");
        userInfoVo.setId(userInfo.getUserId());
        userInfoVo.setIncome(userInfo.getIncome() != null ? userInfo.getIncome() + "K" : "");
        userInfoVo.setMarriage(StringUtils.equals(userInfo.getMarriage(), "已婚") ? 1 : 0);
        userInfoVo.setNickname(userInfo.getNickName());
        userInfoVo.setProfession(userInfo.getIndustry());
        userInfoVo.setPhotoAlbum(userInfo.getPhotoAlbum());
        userInfoVo.setSignature(userInfo.getSignature());
        userInfoVo.setHeight(userInfo.getHeight());
        return userInfoVo;
    }

    public Boolean updateUserInfo(UserInfoVo userInfoVo) {
        User user = UserThreadLocal.get();
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(user.getId());
        // 逐字段 null 安全映射：前端 partial 提交时未传字段为 null，
        // MyBatis-Plus 默认 NOT_NULL 策略跳过 null 列，不会覆盖 DB 已有值
        if (userInfoVo.getAge() != null) {
            userInfo.setAge(Integer.valueOf(userInfoVo.getAge()));
        }
        if (userInfoVo.getGender() != null) {
            userInfo.setSex(StringUtils.equalsIgnoreCase(userInfoVo.getGender(), "man") ? SexEnum.MAN : SexEnum.WOMAN);
        }
        userInfo.setBirthday(userInfoVo.getBirthday());
        userInfo.setCity(userInfoVo.getCity());
        userInfo.setEdu(userInfoVo.getEducation());
        userInfo.setIncome(StringUtils.replaceAll(userInfoVo.getIncome(), "K", ""));
        userInfo.setIndustry(userInfoVo.getProfession());
        if (userInfoVo.getMarriage() != null) {
            userInfo.setMarriage(userInfoVo.getMarriage() == 1 ? "已婚" : "未婚");
        }
        userInfo.setPhotoAlbum(userInfoVo.getPhotoAlbum());
        userInfo.setSignature(userInfoVo.getSignature());
        userInfo.setHeight(userInfoVo.getHeight());
        return this.userInfoApi.updateUserInfoByUserId(userInfo);
    }

    public CountsVo queryCounts() {
        User user = UserThreadLocal.get();

        CountsVo countsVo = new CountsVo();

        countsVo.setEachLoveCount(this.userLikeApi.queryEachLikeCount(user.getId()));
        countsVo.setLoveCount(this.userLikeApi.queryLikeCount(user.getId()));
        countsVo.setFanCount(this.userLikeApi.queryFanCount(user.getId()));
        PageInfo<Visitors> visitorPage = this.visitorsApi.topVisitor(user.getId(), 1, 1);
        countsVo.setVisitorCount(visitorPage != null && visitorPage.getTotal() != null ? visitorPage.getTotal().longValue() : 0L);

        return countsVo;
    }

    public PageResult queryLikeList(Integer type, Integer page, Integer pageSize, String nickname) {
        User user = UserThreadLocal.get();
        PageResult pageResult = new PageResult();
        pageResult.setPage(page);
        pageResult.setPagesize(pageSize);

        // type：1 互相关注 2 我关注 3 粉丝 4 谁看过我
        List<Long> userIds = new ArrayList<>();
        Map<Long, Long> visitTimeMap = new HashMap<>();
        // 用于记录分页总数（各分支查询结果的 total）
        long totalCount = 0;
        switch (type) {
            case 1: {
                PageInfo<UserLike> pageInfo = this.userLikeApi.queryEachLikeList(user.getId(), page, pageSize);
                totalCount = pageInfo.getTotal() != null ? pageInfo.getTotal() : 0;
                for (UserLike record : pageInfo.getRecords()) {
                    userIds.add(record.getUserId());
                }
                break;
            }
            case 2: {
                PageInfo<UserLike> pageInfo = this.userLikeApi.queryLikeList(user.getId(), page, pageSize);
                totalCount = pageInfo.getTotal() != null ? pageInfo.getTotal() : 0;
                for (UserLike record : pageInfo.getRecords()) {
                    userIds.add(record.getLikeUserId());
                }
                break;
            }
            case 3: {
                PageInfo<UserLike> pageInfo = this.userLikeApi.queryFanList(user.getId(), page, pageSize);
                totalCount = pageInfo.getTotal() != null ? pageInfo.getTotal() : 0;
                for (UserLike record : pageInfo.getRecords()) {
                    userIds.add(record.getUserId());
                }
                break;
            }
            case 4: {
                PageInfo<Visitors> pageInfo = this.visitorsApi.topVisitor(user.getId(), page, pageSize);
                totalCount = pageInfo.getTotal() != null ? pageInfo.getTotal() : 0;
                for (Visitors record : pageInfo.getRecords()) {
                    userIds.add(record.getVisitorUserId());
                    visitTimeMap.put(record.getVisitorUserId(), record.getDate());
                }
                break;
            }
            default: {
                break;
            }
        }

        pageResult.setCounts((int) totalCount);
        pageResult.setPages((int) ((totalCount + pageSize - 1) / pageSize));

        if (CollectionUtils.isEmpty(userIds)) {
            return pageResult;
        }

        UserInfoQueryParam queryParam = new UserInfoQueryParam();
        queryParam.setUserIds(userIds);
        if (StringUtils.isNotBlank(nickname)) {
            queryParam.setNickName(nickname);
        }

        List<UserInfo> userInfoList = this.userInfoApi.queryUserInfoList(queryParam);

        List<UserLikeListVo> userLikeListVos = new ArrayList<>();
        for (UserInfo userInfo : userInfoList) {
            UserLikeListVo userLikeListVo = new UserLikeListVo();

            userLikeListVo.setNickname(userInfo.getNickName());
            SexEnum likeSex = userInfo.getSex();
            userLikeListVo.setGender(likeSex != null ? likeSex.name().toLowerCase() : "");
            userLikeListVo.setId(userInfo.getUserId());
            userLikeListVo.setEducation(userInfo.getEdu());
            userLikeListVo.setCity(userInfo.getCity());
            userLikeListVo.setAvatar(userInfo.getLogo());
            userLikeListVo.setAge(userInfo.getAge());
            userLikeListVo.setMarriage(StringUtils.equals(userInfo.getMarriage(), "已婚") ? 1 : 0);

            Double score = this.recommendUserService.queryScore(user.getId(), userInfo.getUserId());
            if (score == 0) {
                score = RandomUtils.nextDouble(30, 90); // 特殊情况下的缘分值的生成，一般情况下都会有值
            }
            userLikeListVo.setMatchRate(score.intValue()); //缘分值
            userLikeListVo.setVisitTime(visitTimeMap.get(userInfo.getUserId())); //来访时间（仅 type=4）

            userLikeListVos.add(userLikeListVo);
        }

        pageResult.setItems(userLikeListVos);
        return pageResult;
    }

    public void disLike(Long userId) {
        User user = UserThreadLocal.get();
        this.userLikeApi.deleteUserLike(user.getId(), userId);
    }

    public MatchResultVo likeFan(Long userId) {
        User user = UserThreadLocal.get();
        this.userLikeApi.saveUserLike(user.getId(), userId);
        // 保存后查询是否互相喜欢：对方也喜欢我即配对成功，前端据此弹庆祝并引导聊天
        Boolean mutual = this.userLikeApi.isMutualLike(user.getId(), userId);
        if (!Boolean.TRUE.equals(mutual)) {
            return new MatchResultVo(false, null);
        }
        UserInfo target = this.userInfoApi.queryByUserId(userId);
        MatchResultVo.MatchedUser matchedUser = null;
        if (target != null) {
            matchedUser = new MatchResultVo.MatchedUser(userId, target.getNickName(), target.getLogo());
        }
        return new MatchResultVo(true, matchedUser);
    }

    public SettingsVo querySettings() {
        User user = UserThreadLocal.get();
        SettingsVo settingsVo = new SettingsVo();
        settingsVo.setPhone(user.getMobile());
        settingsVo.setId(user.getId());

        // 查询通用的设置
        Settings settings = this.settingsService.querySettingsByUserId(user.getId());
        if (null != settings) {
            settingsVo.setPinglunNotification(settings.getPinglunNotification());
            settingsVo.setLikeNotification(settings.getLikeNotification());
            settingsVo.setGonggaoNotification(settings.getGonggaoNotification());
            settingsVo.setMatchNotification(settings.getMatchNotification());
        }

        //查询陌生人问题
        Question question = this.questionService.queryQuestion(user.getId());
        if (null != question) {
            settingsVo.setStrangerQuestion(question.getTxt());
        }

        return settingsVo;
    }

    public void saveQuestions(String content) {
        User user = UserThreadLocal.get();
        this.questionService.save(user.getId(), content);
    }

    public PageResult queryBlacklist(Integer page, Integer pagesize) {

        User user = UserThreadLocal.get();
        IPage<BlackList> iPage = this.blackListService.queryPageList(user.getId(), page, pagesize);

        PageResult pageResult = new PageResult();
        pageResult.setPagesize(pagesize);
        pageResult.setPage(page);
        pageResult.setCounts(new Long(iPage.getTotal()).intValue());
        pageResult.setPages(new Long(iPage.getPages()).intValue());

        List<BlackList> records = iPage.getRecords();
        if (CollectionUtils.isEmpty(records)) {
            return pageResult;
        }

        List<Long> userIds = new ArrayList<>();
        for (BlackList record : records) {
            userIds.add(record.getBlackUserId());
        }

        UserInfoQueryParam queryParam = new UserInfoQueryParam();
        queryParam.setUserIds(userIds);
        List<UserInfo> userInfoList = this.userInfoApi.queryUserInfoList(queryParam);

        List<BlackListVo> blackListVoList = new ArrayList<>();
        for (UserInfo userInfo : userInfoList) {
            BlackListVo blackListVo = new BlackListVo();

            blackListVo.setNickname(userInfo.getNickName());
            blackListVo.setId(userInfo.getUserId());
            SexEnum blackSex = userInfo.getSex();
            blackListVo.setGender(blackSex != null ? blackSex.name().toLowerCase() : "");
            blackListVo.setAvatar(userInfo.getLogo());
            blackListVo.setAge(userInfo.getAge());

            blackListVoList.add(blackListVo);
        }

        pageResult.setItems(blackListVoList);

        return pageResult;
    }

    public void delBlacklist(Long userId) {
        User user = UserThreadLocal.get();
        this.blackListService.delBlacklist(user.getId(), userId);
    }

    public void updateNotification(Boolean likeNotification, Boolean pinglunNotification, Boolean gonggaoNotification, Boolean matchNotification) {
        User user = UserThreadLocal.get();
        this.settingsService.updateNotification(user.getId(), likeNotification, pinglunNotification, gonggaoNotification, matchNotification);
    }

    /**
     * 上报设备推送标识（按 userId+registrationId 去重，存在则更新平台，否则插入）
     *
     * @param registrationId 极光推送标识
     * @param platform       平台 ANDROID/IOS（可空）
     */
    public void reportDevice(String registrationId, String platform) {
        User user = UserThreadLocal.get();
        QueryWrapper<UserDevice> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", user.getId());
        queryWrapper.eq("registration_id", registrationId);
        UserDevice exist = this.userDeviceMapper.selectOne(queryWrapper);
        if (exist != null) {
            exist.setPlatform(platform);
            this.userDeviceMapper.updateById(exist);
        } else {
            UserDevice device = new UserDevice();
            device.setUserId(user.getId());
            device.setRegistrationId(registrationId);
            device.setPlatform(platform);
            this.userDeviceMapper.insert(device);
        }
    }

    /**
     * 提交举报（写入 tb_report，状态 PENDING，由管理端工作台处理）
     *
     * @param targetUserId 被举报用户ID（必填）
     * @param targetType   举报对象类型：USER/POST/COMMENT/MESSAGE
     * @param targetId     举报对象ID（举报用户时与 targetUserId 相同）
     * @param reason       举报原因（必填）
     * @param description  详细描述（可选）
     * @param evidence     证据截图URL，逗号分隔（可选）
     * @return 是否提交成功
     */
    public Boolean submitReport(Long targetUserId, String targetType, Long targetId,
                                String reason, String description, String evidence) {
        User user = UserThreadLocal.get();
        // 不能举报自己
        if (user.getId().equals(targetUserId)) {
            return false;
        }

        Report report = new Report();
        report.setReporterId(user.getId());
        report.setTargetUserId(targetUserId);
        report.setTargetType(StringUtils.isNotBlank(targetType) ? targetType : "USER");
        report.setTargetId(targetId != null ? targetId : targetUserId);
        report.setReason(reason);
        report.setDescription(description);
        report.setEvidence(evidence);
        report.setStatus("PENDING");
        report.setCreated(new Date());
        report.setUpdated(new Date());
        return this.reportMapper.insert(report) > 0;
    }

    /**
     * 注销账号（软删除：tb_user.status=DELETED，数据保留以备恢复，如需彻底清理由运维手工执行）
     * 需校验短信验证码，防止误操作。
     *
     * @param code 短信验证码
     * @return true=注销成功；false=验证码错误或更新失败
     */
    public Boolean logoff(String code) {
        User user = UserThreadLocal.get();
        if (StringUtils.isBlank(code)) {
            return false;
        }

        // 校验验证码：Redis 中的验证码 或 测试账号固定验证码（仅 ID=1）
        String cached = this.redisTemplate.opsForValue().get(CODE_KEY_PREFIX + user.getMobile());
        boolean isTestUser = TEST_USER_ID.equals(user.getId());
        if (isTestUser && TEST_USER_FIXED_CODE.equals(code)) {
            LOGGER.warn("测试账号使用固定验证码注销，userId=1");
        }
        boolean codeValid = code.equals(cached) || (isTestUser && TEST_USER_FIXED_CODE.equals(code));
        if (!codeValid) {
            return false;
        }
        this.redisTemplate.delete(CODE_KEY_PREFIX + user.getMobile());

        // 软删除：status=DELETED（委托 sso 写入 tb_user，BFF 不再直写）
        int rows = Boolean.TRUE.equals(this.userApi.deactivate(user.getId())) ? 1 : 0;

        // 清理推送设备绑定（注销用户不再接收任何推送）
        QueryWrapper<UserDevice> deviceWrapper = new QueryWrapper<>();
        deviceWrapper.eq("user_id", user.getId());
        this.userDeviceMapper.delete(deviceWrapper);

        // 立即踢下线（Sa-Token 按 userId 注销所有会话；loginId 加 "user-" 前缀与 sso 端签发保持一致）
        StpUtil.logout("user-" + user.getId());

        return rows > 0;
    }
}
