package com.xfjy.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;
import com.xfjy.service.api.ESUserLocationApi;
import com.xfjy.service.api.HuanXinApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.api.UserLikeApi;
import com.xfjy.service.enums.SexEnum;
import com.xfjy.service.pojo.RecommendUser;
import com.xfjy.service.pojo.UserInfo;
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

        // 如果未查询到，需要使用默认推荐列表（解析结果已缓存，避免每次请求重复拆分配置字符串）
        if (CollectionUtils.isEmpty(records)) {
            for (Long defaultUserId : getDefaultRecommendUserIds()) {
                RecommendUser recommendUser = new RecommendUser();
                recommendUser.setUserId(defaultUserId);
                recommendUser.setToUserId(user.getId());
                recommendUser.setScore(RandomUtils.nextDouble(70, 98));

                records.add(recommendUser);
            }
        }

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

        double score = this.recommendUserService.queryScore(userId, user.getId());
        if (score == 0) {
            score = 98; //默认分数
        }

        todayBest.setFateValue(Double.valueOf(score).longValue());
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
        UserLocationVo userLocationVo = this.esUserLocationApi.queryByUserId(user.getId());
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
            //使用默认的推荐列表
            String[] ss = StringUtils.split(defaultRecommendUsers, ',');
            for (String s : ss) {
                RecommendUser recommendUser = new RecommendUser();
                recommendUser.setUserId(Long.valueOf(s));
                recommendUser.setToUserId(user.getId());
                records.add(recommendUser);
            }
        }

        List<RecommendUser> newRecommendUserList = new ArrayList<>();

        int showCount = Math.min(10, records.size());
        for (int i = 0; i < showCount; i++) {
            createRecommendUser(newRecommendUserList, records);
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
     * 递归随机生成推荐好友，保证不重复
     */
    private void createRecommendUser(List<RecommendUser> newRecommendUserList,
                                     List<RecommendUser> records){
        RecommendUser recommendUser = records.get(RandomUtils.nextInt(0, records.size() - 1));
        if(!newRecommendUserList.contains(recommendUser)){
            newRecommendUserList.add(recommendUser);
        }else{
            // 开始递归
            createRecommendUser(newRecommendUserList, records);
        }
    }

    /**
     * 喜欢用户（探花右滑/超级喜欢）
     * 保存喜欢关系，若互相喜欢则自动注册为环信好友
     *
     * @param likeUserId 被喜欢的用户ID
     * @return null=保存失败；true=互相喜欢（匹配成功）；false=单方面喜欢
     */
    public Boolean likeUser(Long likeUserId) {
        User user = UserThreadLocal.get();
        String id = this.userLikeApi.saveUserLike(user.getId(), likeUserId);
        if (StringUtils.isEmpty(id)) {
            return null;
        }

        boolean mutualLike = this.userLikeApi.isMutualLike(user.getId(), likeUserId);
        if (mutualLike) {
            // 相互喜欢成为好友（注册环信联系人）
            this.imService.contactUser(likeUserId);
        }
        return mutualLike;
    }

    public Boolean disLikeUser(Long likeUserId) {
        User user = UserThreadLocal.get();
        return this.userLikeApi.deleteUserLike(user.getId(), likeUserId);
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
