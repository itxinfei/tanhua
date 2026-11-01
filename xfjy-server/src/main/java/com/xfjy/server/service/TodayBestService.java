package com.xfjy.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;
import com.xfjy.service.api.ESUserLocationApi;
import com.xfjy.service.api.HuanXinApi;
import com.xfjy.service.api.PayApi;
import com.xfjy.service.api.SwipeLogApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.api.UserLikeApi;
import com.xfjy.service.api.VisitorsApi;
import com.xfjy.service.enums.SexEnum;
import com.xfjy.service.pojo.RecommendUser;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.pojo.Visitors;
import com.xfjy.server.exception.BenefitLimitException;
import com.xfjy.server.exception.SwipeLimitException;
import com.xfjy.service.vo.BenefitCheckVo;
import com.xfjy.service.vo.PageInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.service.vo.UserLocationVo;
import com.xfjy.server.pojo.Question;
import com.xfjy.server.pojo.User;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.server.vo.NearUserVo;
import com.xfjy.server.vo.PageResult;
import com.xfjy.server.vo.RecommendUserQueryParam;
import com.xfjy.server.vo.TodayBest;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TodayBestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TodayBestService.class);

    @Autowired
    private UserService userService;

    @Autowired
    private UserInfoApi userInfoApi;

    @Autowired
    private RecommendUserService recommendUserService;

    @Value("${xfjy.sso.default.user}")
    private Long defaultUserId;

    @Value("${xfjy.sso.default.recommend.users}")
    private String defaultRecommendUsers;

    @Autowired
    private QuestionService questionService;

    private static final JsonMapper MAPPER = new JsonMapper();

    @Autowired
    private HuanXinApi huanXinApi;

    @Autowired
    private UserLikeApi userLikeApi;

        @Autowired //ES版地理位置实现（原Dubbo version=2.0.0）
    private ESUserLocationApi esUserLocationApi;

    @Autowired
    private IMService imService;

    @Autowired //极光推送（匹配成功通知对方，旁路失败不阻断主链路）
    private PushService pushService;

    @Autowired //权益校验（stealth_visit 隐身访问）
    private PayApi payApi;

    @Autowired //访客记录写入
    private VisitorsApi visitorsApi;

    @Autowired //滑动日志记录 + 次数计数（后端兜底拦截用）
    private SwipeLogApi swipeLogApi;

    /** 每日免费滑动上限（与前端 match.ts 的 MAX_FREE_SWIPE=50 一致；最终应迁 Nacos） */
    private static final int MAX_FREE_SWIPE = 50;

    /** 滑动限制拦截时前端跳转页 */
    private static final String SWIPE_LIMIT_UPGRADE_URL = "/pages/mine/vip?source=swipe_out";

    @Autowired //推荐混合评分（阶段1保底：ALS+距离+画像加权）
    private RecommendScoringService recommendScoringService;

    @Autowired //召回过滤（已喜欢排除+异性过滤，评分前调用）
    private RecommendFilterService recommendFilterService;

    @Autowired //冷启动召回（T5：新用户无ALS数据时地理+画像兜底）
    private ColdStartService coldStartService;

    @Autowired //Neo4j 图推荐（P2：喜欢关系同步+二度好友召回，不可用降级）
    private GraphRecommendService graphRecommendService;


    public TodayBest queryTodayBest() {

        //根据token查询当前登录的用户信息
        User user = UserThreadLocal.get();

        TodayBest todayBest = this.recommendUserService.queryTodayBest(user.getId());
        if (todayBest == null) {
            //未找到最高得分的推荐用户，给出一个默认推荐用户
            todayBest = new TodayBest();
            todayBest.setId(defaultUserId);
            todayBest.setFateValue(95L);
        }

        // 补全用户信息
        UserInfo userInfo = this.userInfoApi.queryUserInfoById(todayBest.getId());
        if (null != userInfo) {
            todayBest.setAge(userInfo.getAge());
            todayBest.setAvatar(userInfo.getLogo());
            todayBest.setGender(userInfo.getSex().name().toLowerCase());
            todayBest.setNickname(userInfo.getNickName());
            todayBest.setTags(StringUtils.split(userInfo.getTags(), ','));
        }

        return todayBest;
    }

    public PageResult queryRecommendUserList(RecommendUserQueryParam queryParam) {
        //根据token查询当前登录的用户信息
        User user = UserThreadLocal.get();

        PageInfo<RecommendUser> pageInfo = this.recommendUserService.queryRecommendUserList(user.getId(), queryParam.getPage(), queryParam.getPagesize());
        List<RecommendUser> records = pageInfo.getRecords();

        // T5 冷启动：ALS 无数据时走"地理召回+画像相似度"兜底（内部不足时已用默认推荐用户补尾）
        if (CollectionUtils.isEmpty(records)) {
            records = this.coldStartService.recommendColdStart(user.getId(), queryParam.getPagesize());
        }

        // 召回过滤：排除已喜欢 + 异性过滤（评分前调用，减少无效候选）
        records = this.recommendFilterService.filter(user.getId(), records);

        // 混合评分：ALS+距离+画像加权重排，数据缺失维度自动降级（score 替换为 0~100 缘分值）
        this.recommendScoringService.scoreAndSort(user.getId(), records);

        List<Long> userIds = new ArrayList<>();
        for (RecommendUser record : records) {
            userIds.add(record.getUserId());
        }


        UserInfoQueryParam userInfoQueryParam = new UserInfoQueryParam();
        userInfoQueryParam.setUserIds(userIds); //用户id

        // 按年龄筛选（eq 精确匹配）
        if (queryParam.getAge() != null) {
            userInfoQueryParam.setAge(queryParam.getAge());
        }

        // 按城市筛选（eq 精确匹配）
        if (StringUtils.isNotEmpty(queryParam.getCity())) {
            userInfoQueryParam.setCity(queryParam.getCity());
        }

        //需要查询用户的信息，并且按照条件查询
        List<UserInfo> userInfos = this.userInfoApi.queryUserInfoList(userInfoQueryParam);
        List<TodayBest> todayBests = new ArrayList<>();
        for (UserInfo userInfo : userInfos) {
            TodayBest todayBest = new TodayBest();

            todayBest.setId(userInfo.getUserId());
            todayBest.setAge(userInfo.getAge());
            todayBest.setAvatar(userInfo.getLogo());
            todayBest.setGender(userInfo.getSex().name().toLowerCase());
            todayBest.setNickname(userInfo.getNickName());
            todayBest.setTags(StringUtils.split(userInfo.getTags(), ','));

            for (RecommendUser record : records) {
                if (record.getUserId().longValue() == todayBest.getId().longValue()) {
                    double score = Math.floor(record.getScore());
                    todayBest.setFateValue(Double.valueOf(score).longValue()); //缘分值
                }
            }

            todayBests.add(todayBest);
        }

        //对结果集做排序，按照缘分值倒序排序
        Collections.sort(todayBests, (o1, o2) -> Long.valueOf(o2.getFateValue() - o1.getFateValue()).intValue());

        return new PageResult(0, queryParam.getPagesize(), 0, queryParam.getPage(), todayBests);
    }

    public TodayBest queryTodayBest(Long userId) {

        User user = UserThreadLocal.get();

        TodayBest todayBest = new TodayBest();
        //补全信息
        UserInfo userInfo = this.userInfoApi.queryUserInfoById(userId);
        todayBest.setId(userId);
        todayBest.setAge(userInfo.getAge());
        todayBest.setAvatar(userInfo.getLogo());
        todayBest.setGender(userInfo.getSex().name().toLowerCase());
        todayBest.setNickname(userInfo.getNickName());
        todayBest.setTags(StringUtils.split(userInfo.getTags(), ','));
        todayBest.setCity(userInfo.getCity());
        todayBest.setEdu(userInfo.getEdu());
        todayBest.setIndustry(userInfo.getIndustry());
        todayBest.setPhotoAlbum(userInfo.getPhotoAlbum());

        double score = this.recommendUserService.queryScore(userId, user.getId());
        if (score == 0) {
            score = 98; //默认分数
        }

        todayBest.setFateValue(Double.valueOf(score).longValue());

        // 查看他人详情时记录访客（自己看自己不记录；stealth_visit 隐身权益不记录）
        if (!userId.equals(user.getId())) {
            try {
                BenefitCheckVo stealth = this.payApi.checkBenefit(user.getId(), "stealth_visit");
                if (stealth == null || !Boolean.TRUE.equals(stealth.getHasBenefit())) {
                    Visitors visitors = new Visitors();
                    visitors.setUserId(userId);              // 被查看者
                    visitors.setVisitorUserId(user.getId()); // 来访者
                    visitors.setFrom("todayBest");
                    visitors.setDate(System.currentTimeMillis());
                    this.visitorsApi.saveVisitor(visitors);
                }
            } catch (Exception e) {
                LOGGER.warn("访客记录写入失败：visitor={}, target={}", user.getId(), userId, e);
            }
        }

        return todayBest;
    }

    public String queryQuestion(Long userId) {
        Question question = this.questionService.queryQuestion(userId);
        if (null != question) {
            return question.getTxt();
        }
        return "";
    }

    /**
     * 回复陌生人问题，发送消息给对方
     *
     * @param userId
     * @param reply
     * @return
     */
    public Boolean replyQuestion(Long userId, String reply) {
        User user = UserThreadLocal.get();
        UserInfo userInfo = this.userInfoApi.queryUserInfoById(user.getId());

        //构建消息内容
        Map<String, Object> msg = new HashMap<>();
        msg.put("userId", user.getId().toString());
        msg.put("nickname", this.queryQuestion(userId));
        msg.put("strangerQuestion", userInfo.getNickName());
        msg.put("reply", reply);

        try {
            String msgStr = MAPPER.writeValueAsString(msg);
            // 发送环信透传消息（契约调用 message-service）
            this.huanXinApi.sendMessage(userId.toString(), msgStr);
            return true;
        } catch (Exception e) {
            LOGGER.error("今日佳人查询异常", e);
        }

        return false;
    }

    public List<NearUserVo> queryNearUser(String gender, String distance) {
        User user = UserThreadLocal.get();
        //查询当前用户的地理位置
        // 修改点：当前用户未上报地理位置时 queryByUserId 返回 null，直接解引用会 NPE(500)；无中心点无法计算附近，返回空列表
        UserLocationVo userLocationVo = this.esUserLocationApi.queryByUserId(user.getId());
        if (userLocationVo == null) {
            return Collections.emptyList();
        }
        Double longitude = userLocationVo.getLongitude();
        Double latitude = userLocationVo.getLatitude();

        //查询附近的好友
        List<UserLocationVo> userLocationVoList = this.esUserLocationApi.queryUserFromLocation(longitude, latitude, Integer.valueOf(distance));
        if (CollectionUtils.isEmpty(userLocationVoList)) {
            return Collections.emptyList();
        }

        List<Long> userIds = new ArrayList<>();
        for (UserLocationVo locationVo : userLocationVoList) {
            userIds.add(locationVo.getUserId());
        }

        UserInfoQueryParam userInfoQueryParam = new UserInfoQueryParam();
        userInfoQueryParam.setUserIds(userIds);
        if (StringUtils.equalsIgnoreCase(gender, "man")) {
            userInfoQueryParam.setSex(SexEnum.MAN.getValue());
        } else if (StringUtils.equalsIgnoreCase(gender, "woman")) {
            userInfoQueryParam.setSex(SexEnum.WOMAN.getValue());
        }

        List<UserInfo> userInfoList = this.userInfoApi.queryUserInfoList(userInfoQueryParam);

        List<NearUserVo> result = new ArrayList<>();
        for (UserLocationVo locationVo : userLocationVoList) {
            //排除自己
            if (locationVo.getUserId().longValue() == user.getId().longValue()) {
                continue;
            }

            for (UserInfo userInfo : userInfoList) {
                if (locationVo.getUserId().longValue() == userInfo.getUserId().longValue()) {
                    NearUserVo nearUserVo = new NearUserVo();

                    nearUserVo.setNickname(userInfo.getNickName());
                    nearUserVo.setAvatar(userInfo.getLogo());
                    nearUserVo.setUserId(userInfo.getUserId());
                    nearUserVo.setAge(userInfo.getAge());
                    nearUserVo.setCity(userInfo.getCity());

                    result.add(nearUserVo);
                    break;
                }
            }
        }

        return result;
    }

    public List<TodayBest> queryCardsList() {
        User user = UserThreadLocal.get();

        PageInfo<RecommendUser> pageInfo = this.recommendUserService.queryRecommendUserList(user.getId(), 1, 50);
        List<RecommendUser> records = pageInfo.getRecords();
        if (CollectionUtils.isEmpty(records)) {
            // T5 冷启动兜底（地理召回+画像相似度，不足时默认推荐用户补尾）
            records = this.coldStartService.recommendColdStart(user.getId(), 50);
        }

        // 召回过滤：排除已喜欢 + 异性过滤（评分前调用）
        records = this.recommendFilterService.filter(user.getId(), records);

        // 混合评分后取 top 作为抽样池（保留探索性，非纯 top-N）
        this.recommendScoringService.scoreAndSort(user.getId(), records);

        List<RecommendUser> newRecommendUserList = new ArrayList<>();

        // 抽样池：混合分 top 30，从中随机抽 10 展示
        int poolSize = Math.min(30, records.size());
        List<RecommendUser> candidatePool = records.subList(0, poolSize);
        int showCount = Math.min(10, candidatePool.size());
        for (int i = 0; i < showCount; i++) {
            createRecommendUser(newRecommendUserList, candidatePool);
        }

        List<Long> userIds = new ArrayList<>();
        for (RecommendUser recommendUser : newRecommendUserList) {
            userIds.add(recommendUser.getUserId());
        }

        UserInfoQueryParam userInfoQueryParam = new UserInfoQueryParam();
        userInfoQueryParam.setUserIds(userIds);
        List<UserInfo> userInfoList = this.userInfoApi.queryUserInfoList(userInfoQueryParam);

        List<TodayBest> result = new ArrayList<>();
        for (UserInfo userInfo : userInfoList) {
            TodayBest todayBest = new TodayBest();

            todayBest.setId(userInfo.getUserId());
            todayBest.setAge(userInfo.getAge());
            todayBest.setAvatar(userInfo.getLogo());
            todayBest.setGender(userInfo.getSex().name().toLowerCase());
            todayBest.setNickname(userInfo.getNickName());
            todayBest.setTags(StringUtils.split(userInfo.getTags(), ','));

            result.add(todayBest);
        }

        return result;
    }

    /**
     * 随机生成推荐好友，保证不重复（while 循环重试，避免递归栈溢出）
     */
    private void createRecommendUser(List<RecommendUser> newRecommendUserList,
                                     List<RecommendUser> records){
        while (true) {
            RecommendUser recommendUser = records.get(RandomUtils.nextInt(0, records.size()));
            if (!newRecommendUserList.contains(recommendUser)) {
                newRecommendUserList.add(recommendUser);
                return;
            }
        }
    }

    /**
     * 今日滑动次数校验（兜底层，防前端绕过）
     *
     * <p>逻辑：先检查是否持有 unlimited_swipe 权益；无权益则查询 tb_swipe_log 当日实际次数。</p>
     * <p>注意：dislike（左滑）不计入次数，只有右滑（like/superlike）才计次。</p>
     *
     * @param action 动作类型（用于区分计次策略）
     * @throws SwipeLimitException 次数用尽且无权益
     */
    private void checkSwipeLimit(String action) {
        User user = UserThreadLocal.get();
        // dislike 不占用滑动额度
        if ("dislike".equals(action)) {
            return;
        }
        try {
            BenefitCheckVo benefit = this.payApi.checkBenefit(user.getId(), "unlimited_swipe");
            if (benefit != null && Boolean.TRUE.equals(benefit.getHasBenefit())) {
                return; // VIP 无限滑动 → 放行
            }
        } catch (Exception e) {
            LOGGER.warn("权益校验失败，降级使用计数兜底：userId={}, err={}", user.getId(), e.getMessage());
        }
        // 无权益兜底：以 tb_swipe_log 当日实际写入次数为准
        Long todayCount = this.swipeLogApi.countTodaySwipes(user.getId());
        if (todayCount >= MAX_FREE_SWIPE) {
            throw new SwipeLimitException("swipe_daily_limit", todayCount.intValue(), MAX_FREE_SWIPE, SWIPE_LIMIT_UPGRADE_URL);
        }
    }

    /**
     * 超级喜欢权益校验（后端兜底，防前端绕过 recordBenefitUsage）
     *
     * <p>逻辑：action=superlike 时，检查用户 super_like 权益剩余次数；未开通或次数耗尽 → 抛 BenefitLimitException。</p>
     *
     * @param userId 用户 ID
     * @param action 动作类型
     * @throws BenefitLimitException 权益未开通或今日次数耗尽
     */
    private void checkSuperLikeBenefit(Long userId, String action) {
        if (!"superlike".equals(action)) {
            return; // 普通 like 不校验 super_like
        }
        try {
            BenefitCheckVo benefit = this.payApi.checkBenefit(userId, "super_like");
            if (benefit == null) {
                throw new BenefitLimitException("super_like", "超级喜欢", SWIPE_LIMIT_UPGRADE_URL);
            }
            // 基础权益门槛：VIP lv1 起才能用 super_like
            if (!Boolean.TRUE.equals(benefit.getHasBenefit())) {
                throw new BenefitLimitException("super_like", "超级喜欢", SWIPE_LIMIT_UPGRADE_URL);
            }
            // 每日限次判定（dailyLimit=NULL 表示不限次，仅 VIP 顶级才有）
            if (benefit.getDailyLimit() != null && benefit.getRemainingToday() != null
                    && benefit.getRemainingToday() <= 0) {
                throw new BenefitLimitException("super_like", "超级喜欢", SWIPE_LIMIT_UPGRADE_URL);
            }
            // 后端也扣减一次权益（与前端 recordBenefitUsage 双重兜底；幂等：recordBenefitUsage 内部已防重）
            try {
                this.payApi.recordBenefitUsage(userId, "super_like");
            } catch (Exception e) {
                LOGGER.warn("后端扣减 super_like 权益失败（旁路）：userId={}", userId, e);
            }
        } catch (BenefitLimitException e) {
            throw e; // 直接向上传递，不包装
        } catch (Exception e) {
            LOGGER.warn("super_like 权益校验失败，降级拦截：userId={}, err={}", userId, e.getMessage());
            // 权益服务异常 → 安全降级：拦截 super_like（不允许绕过）
            throw new BenefitLimitException("super_like", "超级喜欢", SWIPE_LIMIT_UPGRADE_URL);
        }
    }

    /**
     * 记录一次滑动动作（旁路：失败不阻断主链路）
     */
    private void recordSwipeLog(Long targetId, String action, boolean matched) {
        try {
            this.swipeLogApi.recordSwipe(UserThreadLocal.get().getId(), targetId, action, matched ? 1 : 0);
        } catch (Exception e) {
            LOGGER.warn("滑动日志写入异常（旁路不阻断）：targetId={}, action={}", targetId, action, e);
        }
    }

    /**
     * 喜欢用户（探花右滑/超级喜欢）
     * 保存喜欢关系，若互相喜欢则自动注册为环信好友
     *
     * @param likeUserId 被喜欢的用户ID
     * @param action 动作类型：like | superlike
     * @return null=保存失败；true=互相喜欢（匹配成功）；false=单方面喜欢
     */
    public Boolean likeUser(Long likeUserId, String action) {
        User user = UserThreadLocal.get();
        // ★ 次数校验兜底（防前端绕过；先验后写）
        checkSwipeLimit(action);
        // ★ super_like 权益校验（仅当 action=superlike 时生效）
        checkSuperLikeBenefit(user.getId(), action);
        String id = this.userLikeApi.saveUserLike(user.getId(), likeUserId);
        if (StringUtils.isEmpty(id)) {
            return null;
        }

        // P2 同步喜欢关系到 Neo4j（异步语义：失败仅打日志，不阻断主链路）
        this.graphRecommendService.syncLikeRelation(user.getId(), likeUserId);

        // ★ 滑动日志记录（用于后端兜底计数；互喜时 matched=1；action 区分 like/superlike）
        boolean mutualLike = this.userLikeApi.isMutualLike(user.getId(), likeUserId);
        recordSwipeLog(likeUserId, action, mutualLike);
        if (mutualLike) {
            // 相互喜欢成为好友（注册环信联系人）
            this.imService.contactUser(likeUserId);
            // 推送匹配成功通知给对方（旁路，失败仅日志不阻断）
            this.pushService.pushMatchNotification(likeUserId);
            // 环信透传匹配消息：App 在线时实时触发"It's a Match"弹窗（旁路，失败仅日志不阻断）
            try {
                UserInfo likeUserInfo = this.userInfoApi.queryUserInfoById(user.getId());
                Map<String, Object> msg = new HashMap<>();
                msg.put("type", "match");
                msg.put("userId", user.getId().toString());
                msg.put("nickname", likeUserInfo != null ? likeUserInfo.getNickName() : "");
                msg.put("avatar", likeUserInfo != null ? likeUserInfo.getLogo() : "");
                this.huanXinApi.sendMessage(likeUserId.toString(), MAPPER.writeValueAsString(msg));
            } catch (Exception e) {
                LOGGER.warn("匹配成功环信消息发送失败：userId={}", likeUserId, e);
            }
        }
        return mutualLike;
    }

    public Boolean disLikeUser(Long likeUserId) {
        User user = UserThreadLocal.get();
        // ★ 次数校验兜底（dislike 不占额度，checkSwipeLimit 内部自动跳过）
        checkSwipeLimit("dislike");
        Boolean deleted = this.userLikeApi.deleteUserLike(user.getId(), likeUserId);

        // P2 同步取消喜欢到 Neo4j（异步语义：失败仅打日志）
        if (Boolean.TRUE.equals(deleted)) {
            this.graphRecommendService.removeLikeRelation(user.getId(), likeUserId);
        }
        return deleted;
    }

    /**
     * 默认推荐用户ID缓存（避免每次请求重复拆分配置字符串）
     */
    private volatile List<Long> defaultRecommendUserIdsCache;

    private List<Long> getDefaultRecommendUserIds() {
        if (defaultRecommendUserIdsCache == null) {
            synchronized (this) {
                if (defaultRecommendUserIdsCache == null) {
                    List<Long> ids = new ArrayList<>();
                    String[] ss = StringUtils.split(defaultRecommendUsers, ',');
                    for (String s : ss) {
                        ids.add(Long.valueOf(s));
                    }
                    defaultRecommendUserIdsCache = Collections.unmodifiableList(ids);
                }
            }
        }
        return defaultRecommendUserIdsCache;
    }
}
