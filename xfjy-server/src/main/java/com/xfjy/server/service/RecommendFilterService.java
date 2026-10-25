package com.xfjy.server.service;

import com.xfjy.service.api.UserLikeApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.enums.SexEnum;
import com.xfjy.service.pojo.RecommendUser;
import com.xfjy.service.pojo.UserLike;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.PageInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 推荐召回结果过滤（T3-2）：已喜欢排除 + 异性过滤（可配置）
 * 在混合评分前调用，减少无效候选参与评分。
 */
@Service
public class RecommendFilterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendFilterService.class);

    /** 拉取喜欢列表的单页大小（用户量小，一次拉全量） */
    private static final int LIKE_LIST_PAGESIZE = 100;

    @Value("${xfjy.recommend.filter.opposite-sex:true}")
    private boolean oppositeSexOnly;

    @Autowired
    private UserLikeApi userLikeApi;

    @Autowired
    private UserInfoApi userInfoApi;

    /**
     * 过滤候选列表：排除已喜欢用户 + 异性过滤（开关控制）
     *
     * @param currentUserId 当前登录用户
     * @param records       待过滤候选
     * @return 过滤后的列表（可能为空，由调用方走默认推荐兜底）
     */
    public List<RecommendUser> filter(Long currentUserId, List<RecommendUser> records) {
        if (CollectionUtils.isEmpty(records)) {
            return records;
        }

        try {
            // 1. 排除已喜欢用户
            Set<Long> likedIds = loadLikedUserIds(currentUserId);

            // 2. 异性过滤
            SexEnum targetSex = null;
            Map<Long, UserInfo> profileMap = Collections.emptyMap();
            if (oppositeSexOnly) {
                UserInfo myProfile = safeQueryProfile(currentUserId);
                if (myProfile != null && myProfile.getSex() != null && myProfile.getSex() != SexEnum.UNKNOWN) {
                    targetSex = (myProfile.getSex() == SexEnum.MAN) ? SexEnum.WOMAN : SexEnum.MAN;
                    profileMap = loadProfiles(records);
                }
            }

            final SexEnum finalTargetSex = targetSex;
            final Map<Long, UserInfo> finalProfileMap = profileMap;
            List<RecommendUser> filtered = records.stream()
                    .filter(r -> !likedIds.contains(r.getUserId()))
                    .filter(r -> finalTargetSex == null // 性别过滤未启用或当前用户性别未知 → 跳过性别过滤
                            || isTargetSex(finalProfileMap.get(r.getUserId()), finalTargetSex))
                    .collect(Collectors.toList());

            LOGGER.debug("召回过滤: 候选{}条 → 过滤后{}条 (已喜欢排除{}, 异性过滤{})",
                    records.size(), filtered.size(), likedIds.size(), finalTargetSex != null);
            return filtered;
        } catch (Exception e) {
            // 过滤失败不阻断推荐链路，返回原列表
            LOGGER.error("召回过滤失败,返回未过滤列表, currentUserId={}", currentUserId, e);
            return records;
        }
    }

    private boolean isTargetSex(UserInfo profile, SexEnum targetSex) {
        // 无画像用户无法判断性别，保守过滤掉（避免推同性）
        return profile != null && profile.getSex() == targetSex;
    }

    private Set<Long> loadLikedUserIds(Long userId) {
        try {
            Set<Long> ids = new HashSet<>();
            int page = 1;
            while (true) {
                PageInfo<UserLike> pageInfo = userLikeApi.queryLikeList(userId, page, LIKE_LIST_PAGESIZE);
                if (pageInfo == null || CollectionUtils.isEmpty(pageInfo.getRecords())) {
                    break;
                }
                for (UserLike like : pageInfo.getRecords()) {
                    ids.add(like.getLikeUserId());
                }
                if (pageInfo.getRecords().size() < LIKE_LIST_PAGESIZE) {
                    break;
                }
                page++;
            }
            return ids;
        } catch (Exception e) {
            LOGGER.warn("拉取喜欢列表失败,已喜欢过滤将降级", e);
            return Collections.emptySet();
        }
    }

    private Map<Long, UserInfo> loadProfiles(List<RecommendUser> records) {
        try {
            List<Long> ids = records.stream()
                    .map(RecommendUser::getUserId)
                    .collect(Collectors.toList());
            UserInfoQueryParam param = new UserInfoQueryParam();
            param.setUserIds(ids);
            List<UserInfo> list = userInfoApi.queryUserInfoList(param);
            if (CollectionUtils.isEmpty(list)) {
                return Collections.emptyMap();
            }
            return list.stream().collect(Collectors.toMap(UserInfo::getUserId, u -> u, (a, b) -> a));
        } catch (Exception e) {
            LOGGER.warn("批量拉取候选画像失败,异性过滤将降级", e);
            return Collections.emptyMap();
        }
    }

    private UserInfo safeQueryProfile(Long userId) {
        try {
            return userInfoApi.queryUserInfoById(userId);
        } catch (Exception e) {
            LOGGER.warn("查询当前用户画像失败,userId={}", userId, e);
            return null;
        }
    }
}