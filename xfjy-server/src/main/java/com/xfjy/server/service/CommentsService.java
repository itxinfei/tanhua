package com.xfjy.server.service;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.pojo.Comment;
import com.xfjy.service.pojo.CommentReplySummary;
import com.xfjy.service.pojo.Publish;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.PageInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.server.pojo.User;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.server.vo.Comments;
import com.xfjy.server.vo.PageResult;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommentsService {

    @Autowired
    private QuanZiApi quanZiApi;

    @Autowired
    private UserInfoApi userInfoApi;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private PushService pushService;

    private static final Logger LOGGER = LoggerFactory.getLogger(CommentsService.class);

    /** 列表接口随顶层评论一起返回的回复预览条数（超过部分前端点「查看全部」再拉） */
    private static final int REPLY_PREVIEW_SIZE = 3;

    private static final DateTimeFormatter COMMENT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");

    /**
     * 查询动态的评论列表
     *
     * @param threaded true=楼中楼（顶层评论分页，每条带回复预览与回复总数）；
     *                 false=扁平列表（顶层评论与回复混排，视频评论沿用旧语义）
     */
    public PageResult queryCommentsList(String publishId, Integer page, Integer pageSize, boolean threaded) {

        PageResult pageResult = new PageResult();
        pageResult.setPage(page);
        pageResult.setPagesize(pageSize);

        PageInfo<Comment> pageInfo = threaded
                ? this.quanZiApi.queryCommentRootList(publishId, page, pageSize)
                : this.quanZiApi.queryCommentList(publishId, page, pageSize);
        int total = pageInfo.getTotal() != null ? pageInfo.getTotal() : 0;
        pageResult.setCounts(total);
        pageResult.setPages((total + pageSize - 1) / pageSize);
        List<Comment> records = pageInfo.getRecords();

        if (CollectionUtils.isEmpty(records)) {
            return pageResult;
        }

        // 楼中楼：一次聚合拿到每条顶层评论的「回复总数 + 最新3条预览」，避免 N+1
        Map<String, CommentReplySummary> replySummary = Collections.emptyMap();
        if (threaded) {
            List<String> rootIds = new ArrayList<>();
            for (Comment record : records) {
                if (record.getId() != null) {
                    rootIds.add(record.getId().toHexString());
                }
            }
            replySummary = queryReplySummarySafe(rootIds);
        }

        // 扁平模式（视频评论等）：回复与顶层评论混排，历史回复无 replyUserId，
        // 需要父评论作者兜底「回复 @某人」，批量预取父评论避免循环内 N 次 RPC
        Map<String, Comment> parentMap = Collections.emptyMap();
        if (!threaded) {
            List<String> parentIds = new ArrayList<>();
            for (Comment record : records) {
                if (record.getParentId() != null && record.getReplyUserId() == null) {
                    String pid = record.getParentId().toHexString();
                    if (!parentIds.contains(pid)) {
                        parentIds.add(pid);
                    }
                }
            }
            if (!parentIds.isEmpty()) {
                try {
                    Map<String, Comment> parents = this.quanZiApi.queryCommentByIdBatch(parentIds);
                    parentMap = parents == null ? Collections.emptyMap() : parents;
                } catch (Exception e) {
                    LOGGER.warn("批量查询父评论失败，被回复人昵称将不展示", e);
                }
            }
        }

        List<Comments> commentsList = this.toComments(records, parentMap);

        // 挂载回复预览：楼下回复的「被回复人」若为历史数据（replyUserId 为空），
        // 兜底显示根评论作者——根评论作者昵称已在上面同一批用户查询里
        for (int idx = 0; idx < commentsList.size() && idx < records.size(); idx++) {
            Comment record = records.get(idx);
            if (record.getId() == null) {
                continue;
            }
            CommentReplySummary summary = replySummary.get(record.getId().toHexString());
            if (summary == null) {
                continue;
            }
            Comments comments = commentsList.get(idx);
            comments.setReplyCount(summary.getTotal() == null ? 0 : summary.getTotal().intValue());
            if (!CollectionUtils.isEmpty(summary.getReplies())) {
                Map<String, Comment> rootAsParent = Collections.singletonMap(
                        record.getId().toHexString(), record);
                comments.setReplies(this.toComments(summary.getReplies(), rootAsParent));
            }
        }

        pageResult.setItems(commentsList);
        return pageResult;
    }

    /**
     * 查询某条顶层评论下的全部回复（「查看全部 N 条回复」分页）
     */
    public PageResult queryCommentReplies(String parentId, Integer page, Integer pageSize) {

        PageResult pageResult = new PageResult();
        pageResult.setPage(page);
        pageResult.setPagesize(pageSize);

        PageInfo<Comment> pageInfo = this.quanZiApi.queryCommentReplyList(parentId, page, pageSize);
        int total = pageInfo.getTotal() != null ? pageInfo.getTotal() : 0;
        pageResult.setCounts(total);
        pageResult.setPages((total + pageSize - 1) / pageSize);

        List<Comment> records = pageInfo.getRecords();
        if (CollectionUtils.isEmpty(records)) {
            return pageResult;
        }

        // 根评论作为「被回复人」兜底映射：历史数据 replyUserId 为空时显示楼主昵称
        Map<String, Comment> rootMap = Collections.emptyMap();
        try {
            Comment root = this.quanZiApi.queryCommentById(parentId);
            if (root != null) {
                rootMap = Collections.singletonMap(parentId, root);
            }
        } catch (Exception e) {
            LOGGER.warn("查询根评论失败，历史回复将不展示被回复人昵称, parentId={}", parentId, e);
        }

        pageResult.setItems(this.toComments(records, rootMap));
        return pageResult;
    }

    /**
     * Comment 实体 -> Comments VO：批量装配昵称、被回复人昵称、点赞数与点赞状态
     *
     * @param fallbackParentMap parentId(hex) -> 父评论；仅用于历史数据（replyUserId 为空）推断被回复人
     */
    private List<Comments> toComments(List<Comment> records, Map<String, Comment> fallbackParentMap) {

        List<Long> userIds = new ArrayList<>();
        for (Comment record : records) {
            collectUserId(userIds, record.getUserId());
            collectUserId(userIds, record.getReplyUserId());
        }
        for (Comment parent : fallbackParentMap.values()) {
            collectUserId(userIds, parent.getUserId());
        }

        Map<Long, String> nicknameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            UserInfoQueryParam queryParam = new UserInfoQueryParam();
            queryParam.setUserIds(userIds);
            try {
                List<UserInfo> userInfoList = this.userInfoApi.queryUserInfoList(queryParam);
                if (!CollectionUtils.isEmpty(userInfoList)) {
                    for (UserInfo userInfo : userInfoList) {
                        if (userInfo != null && userInfo.getUserId() != null) {
                            nicknameMap.put(userInfo.getUserId(), userInfo.getNickName());
                        }
                    }
                }
            } catch (Exception e) {
                // RPC 失败不应让整个评论列表 500，降级为不展示作者信息
                LOGGER.warn("批量查询用户信息失败，评论作者信息将不展示", e);
            }
        }

        // 批量预取 Redis（消除循环内 N 次 hasKey/get）：multiGet 点赞状态 + 点赞数
        Long viewerId = currentViewerId();
        List<String> likeUserCommentKeys = new ArrayList<>();
        List<String> likeCommentKeys = new ArrayList<>();
        for (Comment record : records) {
            String cid = record.getId().toHexString();
            likeUserCommentKeys.add("QUANZI_COMMENT_LIKE_USER_" + viewerId + "_" + cid);
            likeCommentKeys.add("QUANZI_COMMENT_LIKE_" + cid);
        }
        List<String> hasLikedValues;
        List<String> likeCountValues;
        try {
            hasLikedValues = this.redisTemplate.opsForValue().multiGet(likeUserCommentKeys);
            likeCountValues = this.redisTemplate.opsForValue().multiGet(likeCommentKeys);
        } catch (Exception e) {
            LOGGER.warn("批量获取评论点赞缓存失败，降级为未点赞/0赞", e);
            hasLikedValues = null;
            likeCountValues = null;
        }

        List<Comments> commentsList = new ArrayList<>();
        for (int idx = 0; idx < records.size(); idx++) {
            Comment record = records.get(idx);
            Comments comments = new Comments();
            comments.setId(record.getId().toHexString());
            comments.setCreateDate(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(record.getCreated()), ZoneId.systemDefault())
                    .format(COMMENT_TIME_FORMATTER));
            comments.setContent(record.getContent());
            // 语音评论字段（历史数据 mediaType 为 null，统一按文字评论 type=1 透出）
            comments.setType(record.getMediaType() != null ? record.getMediaType() : 1);
            comments.setAudioUrl(record.getAudioUrl());
            comments.setDuration(record.getDuration());
            comments.setNickname(nicknameMap.get(record.getUserId()));

            // 点赞状态：multiGet 结果非 null 即已点赞
            comments.setHasLiked(hasLikedValues != null && hasLikedValues.get(idx) != null ? 1 : 0);

            int likeCount = 0;
            if (likeCountValues != null && likeCountValues.get(idx) != null) {
                try {
                    likeCount = Integer.parseInt(likeCountValues.get(idx));
                } catch (NumberFormatException e) {
                    LOGGER.warn("评论点赞数缓存非数字，兜底为0: key={}, value={}",
                            likeCommentKeys.get(idx), likeCountValues.get(idx));
                }
            }
            comments.setLikeCount(likeCount);

            // 层级信息：parentId + 被回复人昵称
            if (record.getParentId() != null) {
                String pid = record.getParentId().toHexString();
                comments.setParentId(pid);
                Long repliedUserId = record.getReplyUserId();
                if (repliedUserId == null) {
                    // 历史数据：保存时只落了根评论，退化为「回复楼主」
                    Comment parent = fallbackParentMap.get(pid);
                    if (parent != null) {
                        repliedUserId = parent.getUserId();
                    }
                }
                if (repliedUserId != null) {
                    comments.setReplyNickname(nicknameMap.get(repliedUserId));
                }
            }

            commentsList.add(comments);
        }
        return commentsList;
    }

    private void collectUserId(List<Long> userIds, Long userId) {
        if (userId != null && !userIds.contains(userId)) {
            userIds.add(userId);
        }
    }

    private Long currentViewerId() {
        User user = UserThreadLocal.get();
        return user == null ? -1L : user.getId();
    }

    private Map<String, CommentReplySummary> queryReplySummarySafe(List<String> rootIds) {
        if (CollectionUtils.isEmpty(rootIds)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, CommentReplySummary> summary =
                    this.quanZiApi.queryCommentReplySummary(rootIds, REPLY_PREVIEW_SIZE);
            return summary == null ? Collections.emptyMap() : summary;
        } catch (Exception e) {
            // 回复装配失败不影响主评论列表可用性：楼下回复退化为不展示
            LOGGER.warn("批量查询评论回复汇总失败，楼下回复将不展示, rootCount={}", rootIds.size(), e);
            return Collections.emptyMap();
        }
    }

    public Boolean saveComments(String publishId, String content, String replyCommentId,
                                Integer mediaType, String audioUrl, Integer duration) {
        User user = UserThreadLocal.get();
        boolean success;
        if (StringUtils.isNotBlank(replyCommentId)) {
            // 回复评论：由圈子服务归一到根评论后挂载（语音评论暂仅支持顶层，回复仍走文字）
            success = this.quanZiApi.saveReplyComment(user.getId(), publishId, replyCommentId, content);
        } else if (Integer.valueOf(2).equals(mediaType)) {
            // 语音评论：content 可为空，语音地址与时长必传（控制器已校验）
            success = this.quanZiApi.saveComment(user.getId(), publishId, 2, content, 2, audioUrl, duration);
        } else {
            success = this.quanZiApi.saveComment(user.getId(), publishId, 2, content, null, null, null);
        }
        if (!success) {
            return false;
        }
        // 评论通知（旁路：失败仅日志不阻断）：回复他人评论时通知被回复人，否则通知动态作者
        String brief = StringUtils.isNotBlank(content) ? content : "[语音评论]";
        try {
            Long targetUserId = findNotifyTarget(publishId, replyCommentId);
            if (targetUserId != null && !targetUserId.equals(user.getId())) {
                this.pushService.pushCommentNotification(targetUserId, brief);
            }
        } catch (Exception e) {
            LOGGER.warn("评论推送触发失败：publishId={}, replyCommentId={}", publishId, replyCommentId, e);
        }
        return true;
    }

    /**
     * 评论通知目标：回复场景优先通知被回复的评论作者，否则通知动态作者
     */
    private Long findNotifyTarget(String publishId, String replyCommentId) {
        if (StringUtils.isNotBlank(replyCommentId)) {
            try {
                Comment replied = this.quanZiApi.queryCommentById(replyCommentId);
                if (replied != null && replied.getUserId() != null) {
                    return replied.getUserId();
                }
            } catch (Exception e) {
                LOGGER.warn("查询被回复评论失败，转为通知动态作者：replyCommentId={}", replyCommentId, e);
            }
        }
        Publish publish = this.quanZiApi.queryPublishById(publishId);
        return publish == null ? null : publish.getUserId();
    }
}
