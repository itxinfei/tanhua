package com.xfjy.server.service;

import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.api.VideoApi;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.pojo.Video;
import com.xfjy.service.vo.PageInfo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 后台视频内容管理服务
 */
@Service
public class AdminVideoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminVideoService.class);

    @Autowired
    private VideoApi videoApi;

    @Autowired
    private UserInfoApi userInfoApi;

    @Autowired
    private QuanZiApi quanZiApi;

    /**
     * 分页查询视频列表（合并用户信息与互动数据）
     */
    public Map<String, Object> queryVideoPage(Integer page, Integer pageSize,
                                               String keyword, Long userId) {
        PageInfo<Video> videoPage = this.videoApi.queryAllVideoList(page, pageSize, keyword, userId);

        List<Map<String, Object>> items = new ArrayList<>();
        if (videoPage != null && videoPage.getRecords() != null) {
            Set<Long> userIds = new HashSet<>();
            for (Video video : videoPage.getRecords()) {
                if (video.getUserId() != null) {
                    userIds.add(video.getUserId());
                }
            }
            Map<Long, UserInfo> userMap = batchQueryUserInfo(userIds);

            // 视频互动数同样存储在 quanzi_comment 中（publishId=videoId）
            List<String> videoIds = new ArrayList<>();
            for (Video video : videoPage.getRecords()) {
                if (video.getId() != null) {
                    videoIds.add(video.getId().toHexString());
                }
            }
            Map<String, Long> likeCountMap = safeBatchCount(videoIds, 1);
            Map<String, Long> commentCountMap = safeBatchCount(videoIds, 2);

            for (Video video : videoPage.getRecords()) {
                items.add(convertToItem(video, userMap, likeCountMap, commentCountMap));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", videoPage != null ? videoPage.getTotal() : 0);
        result.put("list", items);
        return result;
    }

    /**
     * 删除（下架）视频
     */
    public Boolean deleteVideo(String videoId) {
        if (StringUtils.isBlank(videoId)) {
            return false;
        }
        try {
            return this.videoApi.removeVideo(videoId);
        } catch (Exception e) {
            LOGGER.error("删除视频失败 videoId={}", videoId, e);
            return false;
        }
    }

    /**
     * 批量删除视频
     *
     * @param videoIds 视频ID列表
     * @return {success: n, failed: m}
     */
    public Map<String, Object> batchDeleteVideo(List<String> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("success", 0);
            empty.put("failed", 0);
            return empty;
        }
        try {
            return this.videoApi.batchRemoveVideo(videoIds);
        } catch (Exception e) {
            LOGGER.error("批量删除视频失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", 0);
            result.put("failed", videoIds.size());
            return result;
        }
    }

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

    private Map<String, Long> safeBatchCount(List<String> ids, int type) {
        try {
            Map<String, Long> result = this.quanZiApi.queryCommentCountBatch(ids, type);
            return result != null ? result : Collections.emptyMap();
        } catch (Exception e) {
            LOGGER.warn("批量查询互动数失败 type={}", type, e);
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> convertToItem(Video video,
                                               Map<Long, UserInfo> userMap,
                                               Map<String, Long> likeCountMap,
                                               Map<String, Long> commentCountMap) {
        Map<String, Object> item = new HashMap<>();
        String hexId = video.getId() != null ? video.getId().toHexString() : "";
        item.put("id", hexId);
        item.put("vid", video.getVid());
        item.put("userId", video.getUserId());
        item.put("text", video.getText());
        item.put("picUrl", video.getPicUrl());
        item.put("videoUrl", video.getVideoUrl());
        item.put("seeType", video.getSeeType());
        item.put("locationName", video.getLocationName());
        item.put("created", video.getCreated());

        UserInfo userInfo = userMap.get(video.getUserId());
        if (userInfo != null) {
            item.put("userNickname", userInfo.getNickName());
            item.put("userAvatar", userInfo.getLogo());
        } else {
            item.put("userNickname", "用户" + video.getUserId());
            item.put("userAvatar", "");
        }

        item.put("likeCount", likeCountMap.getOrDefault(hexId, 0L));
        item.put("commentCount", commentCountMap.getOrDefault(hexId, 0L));
        return item;
    }
}