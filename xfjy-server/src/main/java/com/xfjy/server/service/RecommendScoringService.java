package com.xfjy.server.service;

import com.xfjy.service.api.ESUserLocationApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.pojo.RecommendUser;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.service.vo.UserLocationVo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 推荐混合评分服务（推荐引擎重构阶段1保底方案）
 *
 * <p>评分公式：finalScore = Σ(wi·si) / Σ(wi可用)，某维度数据缺失时该维度降级（权重不计入分母）。
 * <ul>
 *   <li>ALS 分数：recommend_user 表的协同过滤分，归一化到 0~1</li>
 *   <li>地理距离：Haversine 距离衰减 1/(1+d/decayKm)，当前用户无位置则整体降级</li>
 *   <li>画像相似度：tags Jaccard×0.5 + 年龄差衰减×0.3 + 同城×0.2</li>
 * </ul>
 * 输出分数映射到 0~100 区间（缘分值），权重通过 application.yml 热调。
 */
@Service
public class RecommendScoringService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendScoringService.class);

    /** 地理位置拉取范围（km），用于一次性拉取候选用户位置 */
    private static final int GEO_QUERY_RANGE_KM = 500;

    @Value("${xfjy.recommend.scoring.w-als:0.5}")
    private double weightAls;

    @Value("${xfjy.recommend.scoring.w-geo:0.3}")
    private double weightGeo;

    @Value("${xfjy.recommend.scoring.w-profile:0.2}")
    private double weightProfile;

    @Value("${xfjy.recommend.scoring.geo-decay-km:50}")
    private double geoDecayKm;

    @Autowired
    private ESUserLocationApi esUserLocationApi;

    @Autowired
    private UserInfoApi userInfoApi;

    /**
     * 对候选列表计算混合分并按降序排序。
     * 每条记录的 score 字段替换为混合分（0~100），数据缺失维度自动降级。
     *
     * @param currentUserId 当前登录用户ID
     * @param records       ALS 候选列表（原地修改 score 并排序）
     * @return 同一列表引用，按混合分降序排序
     */
    public List<RecommendUser> scoreAndSort(Long currentUserId, List<RecommendUser> records) {
        if (CollectionUtils.isEmpty(records) || records.size() <= 1) {
            return records;
        }

        try {
            List<Long> candidateIds = records.stream()
                    .map(RecommendUser::getUserId)
                    .collect(Collectors.toList());

            // 批量拉取候选画像
            Map<Long, UserInfo> profileMap = loadProfiles(candidateIds);
            UserInfo myProfile = safeQueryProfile(currentUserId);

            // 拉取位置数据（当前用户无位置则距离维度整体降级）
            UserLocationVo myLocation = safeQueryLocation(currentUserId);
            Map<Long, UserLocationVo> locationMap = (myLocation != null)
                    ? loadLocations(myLocation) : Collections.emptyMap();

            // ALS 归一化基准（除以候选最大分）
            double maxAls = records.stream()
                    .mapToDouble(RecommendUser::getScore)
                    .max().orElse(1.0);
            if (maxAls <= 0) {
                maxAls = 1.0;
            }

            for (RecommendUser record : records) {
                double finalScore = computeScore(record, maxAls, myProfile,
                        profileMap.get(record.getUserId()), myLocation,
                        locationMap.get(record.getUserId()));
                record.setScore(finalScore);
            }

            records.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        } catch (Exception e) {
            // 评分失败不阻断推荐链路，降级为原 ALS 排序
            LOGGER.error("混合评分计算失败,降级为ALS排序, currentUserId={}", currentUserId, e);
        }

        return records;
    }

    /**
     * 单候选评分：可用维度加权求和后按可用权重归一，结果 ×100 映射到缘分值区间
     */
    private double computeScore(RecommendUser record, double maxAls,
                                UserInfo myProfile, UserInfo candProfile,
                                UserLocationVo myLocation, UserLocationVo candLocation) {
        // ALS 维度始终可用（候选来自 ALS 结果或默认推荐）
        double sAls = clamp01(record.getScore() / maxAls);

        double sGeo = -1;
        if (myLocation != null && candLocation != null) {
            double distKm = haversineKm(myLocation.getLongitude(), myLocation.getLatitude(),
                    candLocation.getLongitude(), candLocation.getLatitude());
            sGeo = 1.0 / (1.0 + distKm / geoDecayKm);
        }

        double sProfile = -1;
        if (myProfile != null && candProfile != null) {
            sProfile = profileSimilarity(myProfile, candProfile);
        }

        double sum = weightAls * sAls;
        double wsum = weightAls;
        if (sGeo >= 0) {
            sum += weightGeo * sGeo;
            wsum += weightGeo;
        }
        if (sProfile >= 0) {
            sum += weightProfile * sProfile;
            wsum += weightProfile;
        }

        return (wsum > 0 ? sum / wsum : 0) * 100;
    }

    /**
     * 画像相似度：tags Jaccard×0.5 + 年龄差衰减×0.3 + 同城×0.2
     */
    private double profileSimilarity(UserInfo mine, UserInfo cand) {
        double tagSim = jaccard(splitTags(mine.getTags()), splitTags(cand.getTags()));
        double ageSim = ageSimilarity(mine.getAge(), cand.getAge());
        double citySim = StringUtils.equals(mine.getCity(), cand.getCity()) ? 1.0 : 0.0;
        return 0.5 * tagSim + 0.3 * ageSim + 0.2 * citySim;
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
        return clamp01(1.0 - Math.abs(age1 - age2) / 30.0);
    }

    private Set<String> splitTags(String tags) {
        if (StringUtils.isEmpty(tags)) {
            return Collections.emptySet();
        }
        return Arrays.stream(StringUtils.split(tags, ','))
                .map(String::trim)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toSet());
    }

    /**
     * Haversine 公式计算两点间球面距离（km）
     */
    private double haversineKm(double lng1, double lat1, double lng2, double lat2) {
        double earthRadius = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private Map<Long, UserInfo> loadProfiles(List<Long> userIds) {
        try {
            UserInfoQueryParam param = new UserInfoQueryParam();
            param.setUserIds(userIds);
            List<UserInfo> list = userInfoApi.queryUserInfoList(param);
            if (CollectionUtils.isEmpty(list)) {
                return Collections.emptyMap();
            }
            return list.stream().collect(Collectors.toMap(UserInfo::getUserId, u -> u, (a, b) -> a));
        } catch (Exception e) {
            LOGGER.warn("批量拉取候选画像失败,画像维度将降级", e);
            return Collections.emptyMap();
        }
    }

    private Map<Long, UserLocationVo> loadLocations(UserLocationVo center) {
        try {
            List<UserLocationVo> list = esUserLocationApi.queryUserFromLocation(
                    center.getLongitude(), center.getLatitude(), GEO_QUERY_RANGE_KM);
            if (CollectionUtils.isEmpty(list)) {
                return Collections.emptyMap();
            }
            return list.stream().collect(Collectors.toMap(UserLocationVo::getUserId, v -> v, (a, b) -> a));
        } catch (Exception e) {
            LOGGER.warn("拉取范围内用户位置失败,距离维度将降级", e);
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

    private UserLocationVo safeQueryLocation(Long userId) {
        try {
            return esUserLocationApi.queryByUserId(userId);
        } catch (Exception e) {
            LOGGER.warn("查询当前用户位置失败,userId={}", userId, e);
            return null;
        }
    }
}