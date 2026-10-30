package com.xfjy.server.service;

import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.pojo.Publish;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.PageInfo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 后台动态内容管理服务
 */
@Service
public class AdminMovementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminMovementService.class);

    @Autowired
    private QuanZiApi quanZiApi;

    @Autowired
    private UserInfoApi userInfoApi;

    /**
     * 分页查询动态列表（合并用户信息与互动数据）
     */
    public Map<String, Object> queryMovementPage(Integer page, Integer pageSize,
                                                  String keyword, Long userId,
                                                  Integer seeType,
                                                  String startDate, String endDate) {
        Long startTime = parseDateToTimestamp(startDate);
        Long endTime = parseDateToTimestamp(endDate);
        PageInfo<Publish> publishPage = this.quanZiApi.queryAllPublishList(page, pageSize, keyword, userId, startTime, endTime);

        List<Map<String, Object>> items = new ArrayList<>();
        if (publishPage != null && publishPage.getRecords() != null) {
            // seeType 过滤：在 Service 层过滤，避免改动 QuanZiApi 接口
            List<Publish> filteredRecords = new ArrayList<>();
            if (seeType != null) {
                for (Publish publish : publishPage.getRecords()) {
                    if (publish.getSeeType() != null && publish.getSeeType().equals(seeType)) {
                        filteredRecords.add(publish);
                    }
                }
            } else {
                filteredRecords = new ArrayList<>(publishPage.getRecords());
            }

            // 批量收集用户ID，减少远程调用
            Set<Long> userIds = new HashSet<>();
            for (Publish publish : filteredRecords) {
                if (publish.getUserId() != null) {
                    userIds.add(publish.getUserId());
                }
            }
            Map<Long, UserInfo> userMap = batchQueryUserInfo(userIds);

            // 批量查询互动数（点赞1/评论2/喜欢3）
            List<String> publishIds = new ArrayList<>();
            for (Publish publish : filteredRecords) {
                if (publish.getId() != null) {
                    publishIds.add(publish.getId().toHexString());
                }
            }
            Map<String, Long> likeCountMap = safeBatchCount(publishIds, 1);
            Map<String, Long> commentCountMap = safeBatchCount(publishIds, 2);
            Map<String, Long> loveCountMap = safeBatchCount(publishIds, 3);

            for (Publish publish : filteredRecords) {
                items.add(convertToItem(publish, userMap, likeCountMap, commentCountMap, loveCountMap));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", seeType != null && publishPage != null ? items.size() : (publishPage != null ? publishPage.getTotal() : 0));
        result.put("list", items);
        return result;
    }

    /**
     * 删除（下架）动态
     */
    public Boolean deleteMovement(String publishId) {
        if (StringUtils.isBlank(publishId)) {
            return false;
        }
        try {
            return this.quanZiApi.removePublish(publishId);
        } catch (Exception e) {
            LOGGER.error("删除动态失败 publishId={}", publishId, e);
            return false;
        }
    }

    /**
     * 批量删除动态
     *
     * @param publishIds 动态ID列表
     * @return {success: n, failed: m}
     */
    public Map<String, Object> batchDeleteMovement(List<String> publishIds) {
        if (publishIds == null || publishIds.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("success", 0);
            empty.put("failed", 0);
            return empty;
        }
        try {
            return this.quanZiApi.batchRemovePublish(publishIds);
        } catch (Exception e) {
            LOGGER.error("批量删除动态失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", 0);
            result.put("failed", publishIds.size());
            return result;
        }
    }

    /**
     * 批量查询用户信息（单次逐个查询，失败不阻断列表）
     */
    private Map<Long, UserInfo> batchQueryUserInfo(Set<Long> userIds) {
        Map<Long, UserInfo> userMap = new HashMap<>();
        for (Long uid : userIds) {
            try {
                UserInfo userInfo = this.userInfoApi.queryByUserId(uid);
                if (userInfo != null) {
                    userMap.put(uid, userInfo);
                }
            } catch (Exception e) {
                LOGGER.warn("查询用户信息失败 userId={}", uid, e);
            }
        }
        return userMap;
    }

    /**
     * 批量查询互动数（失败返回空Map，不阻断列表）
     */
    private Map<String, Long> safeBatchCount(List<String> publishIds, int type) {
        try {
            Map<String, Long> result = this.quanZiApi.queryCommentCountBatch(publishIds, type);
            return result != null ? result : Collections.emptyMap();
        } catch (Exception e) {
            LOGGER.warn("批量查询互动数失败 type={}", type, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Publish转前端Item
     */
    private Map<String, Object> convertToItem(Publish publish,
                                               Map<Long, UserInfo> userMap,
                                               Map<String, Long> likeCountMap,
                                               Map<String, Long> commentCountMap,
                                               Map<String, Long> loveCountMap) {
        Map<String, Object> item = new HashMap<>();
        String hexId = publish.getId() != null ? publish.getId().toHexString() : "";
        item.put("id", hexId);
        item.put("pid", publish.getPid());
        item.put("userId", publish.getUserId());
        item.put("text", publish.getText());
        item.put("medias", publish.getMedias() != null ? publish.getMedias() : Collections.emptyList());
        item.put("seeType", publish.getSeeType());
        item.put("locationName", publish.getLocationName());
        item.put("created", publish.getCreated());

        UserInfo userInfo = userMap.get(publish.getUserId());
        if (userInfo != null) {
            item.put("userNickname", userInfo.getNickName());
            item.put("userAvatar", userInfo.getLogo());
        } else {
            item.put("userNickname", "用户" + publish.getUserId());
            item.put("userAvatar", "");
        }

        item.put("likeCount", likeCountMap.getOrDefault(hexId, 0L));
        item.put("commentCount", commentCountMap.getOrDefault(hexId, 0L));
        item.put("loveCount", loveCountMap.getOrDefault(hexId, 0L));
        return item;
    }

    /**
     * 将 yyyy-MM-dd 日期字符串转为当天 00:00:00 的时间戳（毫秒）
     */
    private Long parseDateToTimestamp(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        try {
            return java.sql.Date.valueOf(dateStr).getTime();
        } catch (Exception e) {
            LOGGER.warn("日期解析失败: {}", dateStr);
            return null;
        }
    }
}