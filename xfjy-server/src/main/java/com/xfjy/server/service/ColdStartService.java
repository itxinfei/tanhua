package com.xfjy.server.service;

import com.xfjy.service.api.ESUserLocationApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.pojo.RecommendUser;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.UserLocationVo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * T5 冷启动召回：新用户无 ALS 数据时的"地理召回 + 画像相似度"兜底。
 *
 * <p>开关关闭或数据不足时用默认推荐用户补尾，保证推荐链路不空。
 * 内部相似度分（0~1）×100 取整写入 RecommendUser.score（缘分值区间，与混合评分输出对齐）。
 */
@Service
public class ColdStartService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ColdStartService.class);

    /** 地理召回半径（km） */
    private static final int GEO_RECALL_RADIUS_KM = 500;
    /** 默认补尾用户的缘分值 */
    private static final double DEFAULT_FALLBACK_SCORE = 80.0;

    @Value("${xfjy.recommend.cold-start.enabled:true}")
    private boolean enabled;

    @Value("${xfjy.recommend.cold-start.fallback-size:20}")
    private int fallbackSize;

    @Value("${xfjy.sso.default.recommend.users:}")
    private String defaultRecommendUsers;

    @Autowired
    private ESUserLocationApi esUserLocationApi;

    @Autowired
    private UserInfoApi userInfoApi;

    /**
     * 冷启动推荐：地理召回附近用户，按画像相似度打分，不足时默认推荐用户补尾。
     *
     * @param userId   当前用户 id
     * @param pagesize 期望召回数量（null/<=0 时取 fallback-size）
     * @return 推荐候选列表（score 为 0~100 缘分值），可能不足 pagesize（数据耗尽时）
     */
    public List<RecommendUser> recommendColdStart(Long userId, Integer pagesize) {
        if (!enabled || userId == null) {
            return Collections.emptyList();
        }
        int size = (pagesize != null && pagesize > 0) ? pagesize : fallbackSize;
        List<RecommendUser> result = new ArrayList<>();
        Set<Long> existIds = new HashSet<>();

        // 1. 当前用户画像（画像相似度基准）
        UserInfo myProfile = safeQueryProfile(userId);

        // 2. 地理召回：ES 附近用户
        List<UserLocationVo> nearby = safeQueryNearby(userId);
        if (CollectionUtils.isNotEmpty(nearby)) {
            List<Long> candidateIds = nearby.stream()
                    .map(UserLocationVo::getUserId)
                    .filter(id -> id != null && !id.equals(userId))
                    .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(candidateIds)) {
                List<UserInfo> profiles = safeQueryProfiles(candidateIds);
                for (UserInfo profile : profiles) {
                    if (result.size() >= size) {
                        break;
                    }
                    if (existIds.add(profile.getUserId())) {
                        result.add(buildRecommendUser(userId, profile, myProfile));
                    }
                }
            }
        }

        // 3. 不足时默认推荐用户补尾
        if (result.size() < size) {
            for (Long defaultId : parseDefaultRecommendUsers()) {
                if (result.size() >= size) {
                    break;
                }
                if (defaultId.equals(userId) || !existIds.add(defaultId)) {
                    continue;
                }
                RecommendUser ru = new RecommendUser();
                ru.setUserId(defaultId);
                ru.setToUserId(userId);
                ru.setScore(DEFAULT_FALLBACK_SCORE);
                result.add(ru);
            }
        }

        LOGGER.info("冷启动召回完成, userId={}, 召回={}/{}", userId, result.size(), size);
        return result;
    }

    /**
     * 构建推荐候选：画像相似度分（0~1）×100 取整写入 score。
     * 相似度 = tags Jaccard×0.5 + 年龄差衰减×0.3 + 同城×0.2（与混合评分画像维度公式一致）。
     */
    private RecommendUser buildRecommendUser(Long userId, UserInfo profile, UserInfo myProfile) {
        RecommendUser ru = new RecommendUser();
        ru.setUserId(profile.getUserId());
        ru.setToUserId(userId);
        double score = 0.5; // 无基准画像时给中位分
        if (myProfile != null) {
            score = profileSimilarity(myProfile, profile);
        }
        ru.setScore((double) Math.round(score * 100));
        return ru;
    }

    private double profileSimilarity(UserInfo mine, UserInfo cand) {
        double tagSim = jaccard(splitTags(mine.getTags()), splitTags(cand.getTags()));
        double ageSim = ageSimilarity(mine.getAge(), cand.getAge());
        double citySim = StringUtils.equals(mine.getCity(), cand.getCity()) ? 1.0 : 0.0;
        return 0.5 * tagSim + 0.3 * ageSim + 0.2 * citySim;
    }

    private Set<String> splitTags(String tags) {
        if (StringUtils.isBlank(tags)) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.asList(tags.split(",")));
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private double ageSimilarity(Integer age1, Integer age2) {
        if (age1 == null || age2 == null) {
            return 0;
        }
        int diff = Math.abs(age1 - age2);
        return Math.max(0, 1.0 - diff / 10.0);
    }

    private UserInfo safeQueryProfile(Long userId) {
        try {
            return this.userInfoApi.queryUserInfoById(userId);
        } catch (Exception e) {
            LOGGER.warn("冷启动画像查询失败, userId={}", userId, e);
            return null;
        }
    }

    private List<UserInfo> safeQueryProfiles(List<Long> userIds) {
        try {
            com.xfjy.service.vo.UserInfoQueryParam param = new com.xfjy.service.vo.UserInfoQueryParam();
            param.setUserIds(userIds);
            List<UserInfo> list = this.userInfoApi.queryUserInfoList(param);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            LOGGER.warn("冷启动候选画像批量查询失败", e);
            return Collections.emptyList();
        }
    }

    private List<UserLocationVo> safeQueryNearby(Long userId) {
        try {
            UserLocationVo myLocation = this.esUserLocationApi.queryByUserId(userId);
            if (myLocation == null || myLocation.getLongitude() == null || myLocation.getLatitude() == null) {
                return Collections.emptyList();
            }
            List<UserLocationVo> nearby = this.esUserLocationApi.queryUserFromLocation(
                    myLocation.getLongitude(), myLocation.getLatitude(), GEO_RECALL_RADIUS_KM);
            return nearby != null ? nearby : Collections.emptyList();
        } catch (Exception e) {
            LOGGER.warn("冷启动地理召回失败, userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    private List<Long> parseDefaultRecommendUsers() {
        if (StringUtils.isBlank(defaultRecommendUsers)) {
            return Collections.emptyList();
        }
        return Arrays.stream(defaultRecommendUsers.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotEmpty)
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }
}