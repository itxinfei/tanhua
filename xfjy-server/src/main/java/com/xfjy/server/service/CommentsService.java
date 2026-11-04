package com.xfjy.server.service;

import org.springframework.util.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.pojo.Comment;
import com.xfjy.service.pojo.Publish;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.PageInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.server.pojo.User;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.server.vo.Comments;
import com.xfjy.server.vo.PageResult;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    public PageResult queryCommentsList(String publishId, Integer page, Integer pageSize) {

        User user = UserThreadLocal.get();

        PageResult pageResult = new PageResult();
        pageResult.setPage(page);
        pageResult.setPagesize(pageSize);

        PageInfo<Comment> pageInfo = this.quanZiApi.queryCommentList(publishId, page, pageSize);
        int total = pageInfo.getTotal() != null ? pageInfo.getTotal() : 0;
        pageResult.setCounts(total);
        pageResult.setPages((total + pageSize - 1) / pageSize);
        List<Comment> records = pageInfo.getRecords();

        if (CollectionUtils.isEmpty(records)) {
            return pageResult;
        }

        List<Long> userIds = new ArrayList<>();
        for (Comment record : records) {
            if(!userIds.contains(record.getUserId())){
                userIds.add(record.getUserId());
            }
        }

        // 批量预取父评论（消除循环内 N 次 RPC）：收集 parentId -> 一次 $in 查询
        List<String> parentIds = new ArrayList<>();
        for (Comment record : records) {
            if (record.getParentId() != null) {
                String pid = record.getParentId().toHexString();
                if (!parentIds.contains(pid)) {
                    parentIds.add(pid);
                }
            }
        }
        Map<String, Comment> parentCommentMap = new HashMap<>();
        if (!parentIds.isEmpty()) {
            try {
                parentCommentMap = this.quanZiApi.queryCommentByIdBatch(parentIds);
            } catch (Exception e) {
                LOGGER.warn("批量查询父评论失败，被回复人昵称将不展示", e);
            }
        }
        // 父评论作者也加入批量用户查询范围
        for (Comment parent : parentCommentMap.values()) {
            if (parent.getUserId() != null && !userIds.contains(parent.getUserId())) {
                userIds.add(parent.getUserId());
            }
        }

        UserInfoQueryParam queryParam = new UserInfoQueryParam();
        queryParam.setUserIds(userIds);
        List<UserInfo> userInfoList;
        try {
            userInfoList = this.userInfoApi.queryUserInfoList(queryParam);
        } catch (Exception e) {
            // RPC 失败不应让整个评论列表 500，降级为不展示作者信息
            LOGGER.warn("批量查询用户信息失败，评论作者信息将不展示", e);
            userInfoList = new ArrayList<>();
        }

        // 批量预取 Redis（消除循环内 N 次 hasKey/get）：multiGet 点赞数 + 点赞状态
        Long viewerId = user == null ? -1L : user.getId();
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
            comments.setCreateDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(record.getCreated()), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm")));
            comments.setContent(record.getContent());
            // 语音评论字段（历史数据 mediaType 为 null，统一按文字评论 type=1 透出）
            comments.setType(record.getMediaType() != null ? record.getMediaType() : 1);
            comments.setAudioUrl(record.getAudioUrl());
            comments.setDuration(record.getDuration());

            if (!CollectionUtils.isEmpty(userInfoList)) {
                for (UserInfo userInfo : userInfoList) {
                    if (userInfo != null && userInfo.getUserId() != null && record.getUserId() != null
                            && record.getUserId().longValue() == userInfo.getUserId().longValue()) {
                        comments.setNickname(userInfo.getNickName());
                        comments.setAvatar(userInfo.getLogo());
                        break;
                    }
                }
            }

            // 点赞状态：multiGet 结果非 null 即已点赞
            comments.setHasLiked(hasLikedValues != null && hasLikedValues.get(idx) != null ? 1 : 0);

            // 点赞数：multiGet 结果解析，非数字兜底为 0
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

            // 层级信息：从预取的父评论映射中取被回复人昵称（无额外 RPC）
            if (record.getParentId() != null) {
                comments.setParentId(record.getParentId().toHexString());
                Comment parent = parentCommentMap.get(comments.getParentId());
                if (parent != null && parent.getUserId() != null && !CollectionUtils.isEmpty(userInfoList)) {
                    for (UserInfo userInfo : userInfoList) {
                        if (userInfo != null && userInfo.getUserId() != null
                                && parent.getUserId().longValue() == userInfo.getUserId().longValue()) {
                            comments.setReplyNickname(userInfo.getNickName());
                            break;
                        }
                    }
                }
            }

            commentsList.add(comments);
        }

        pageResult.setItems(commentsList);

        return pageResult;
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
        // 推送评论通知给动态作者（旁路：自己评论自己不推，失败仅日志不阻断）
        try {
            Publish publish = this.quanZiApi.queryPublishById(publishId);
            if (publish != null && publish.getUserId() != null
                    && !publish.getUserId().equals(user.getId())) {
                this.pushService.pushCommentNotification(publish.getUserId(),
                        StringUtils.isNotBlank(content) ? content : "[语音评论]");
            }
        } catch (Exception e) {
            LOGGER.warn("评论推送触发失败：publishId={}", publishId, e);
        }
        return true;
    }
}
