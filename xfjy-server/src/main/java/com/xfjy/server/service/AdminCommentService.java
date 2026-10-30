package com.xfjy.server.service;

import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.pojo.Comment;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.PageInfo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 后台评论管理服务
 */
@Service
public class AdminCommentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminCommentService.class);

    @Autowired
    private QuanZiApi quanZiApi;

    @Autowired
    private UserInfoApi userInfoApi;

    /**
     * 分页查询评论列表（合并评论人信息）
     */
    public Map<String, Object> queryCommentPage(Integer page, Integer pageSize,
                                                 String keyword, Long userId,
                                                 String startDate, String endDate) {
        Long startTime = parseDateToTimestamp(startDate);
        Long endTime = parseDateToTimestamp(endDate);
        PageInfo<Comment> commentPage = this.quanZiApi.queryAllCommentList(page, pageSize, keyword, userId, startTime, endTime);

        List<Map<String, Object>> items = new ArrayList<>();
        if (commentPage != null && commentPage.getRecords() != null) {
            // 批量收集用户ID
            Set<Long> userIds = new HashSet<>();
            for (Comment comment : commentPage.getRecords()) {
                if (comment.getUserId() != null) {
                    userIds.add(comment.getUserId());
                }
            }
            Map<Long, UserInfo> userMap = batchQueryUserInfo(userIds);

            for (Comment comment : commentPage.getRecords()) {
                items.add(convertToItem(comment, userMap));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", commentPage != null ? commentPage.getTotal() : 0);
        result.put("list", items);
        return result;
    }

    /**
     * 删除评论
     */
    public Boolean deleteComment(String commentId) {
        if (StringUtils.isBlank(commentId)) {
            return false;
        }
        try {
            return this.quanZiApi.removeCommentById(commentId);
        } catch (Exception e) {
            LOGGER.error("删除评论失败 commentId={}", commentId, e);
            return false;
        }
    }

    /**
     * 批量删除评论
     *
     * @param commentIds 评论ID列表
     * @return {success: n, failed: m}
     */
    public Map<String, Object> batchDeleteComment(List<String> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("success", 0);
            empty.put("failed", 0);
            return empty;
        }
        try {
            return this.quanZiApi.batchRemoveCommentByIds(commentIds);
        } catch (Exception e) {
            LOGGER.error("批量删除评论失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", 0);
            result.put("failed", commentIds.size());
            return result;
        }
    }

    /**
     * 批量查询用户信息（失败不阻断列表）
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
     * Comment转前端Item
     */
    private Map<String, Object> convertToItem(Comment comment, Map<Long, UserInfo> userMap) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", comment.getId() != null ? comment.getId().toHexString() : "");
        item.put("publishId", comment.getPublishId() != null ? comment.getPublishId().toHexString() : "");
        item.put("content", comment.getContent());
        item.put("userId", comment.getUserId());
        item.put("publishUserId", comment.getPublishUserId());
        item.put("created", comment.getCreated());

        UserInfo userInfo = userMap.get(comment.getUserId());
        if (userInfo != null) {
            item.put("userNickname", userInfo.getNickName());
            item.put("userAvatar", userInfo.getLogo());
        } else {
            item.put("userNickname", "用户" + comment.getUserId());
            item.put("userAvatar", "");
        }
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