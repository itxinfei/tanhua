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
import java.util.List;

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
        pageResult.setCounts(0);
        pageResult.setPages(0);

        PageInfo<Comment> pageInfo = this.quanZiApi.queryCommentList(publishId, page, pageSize);
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

        UserInfoQueryParam queryParam = new UserInfoQueryParam();
        queryParam.setUserIds(userIds);
        List<UserInfo> userInfoList = this.userInfoApi.queryUserInfoList(queryParam);

        List<Comments> commentsList = new ArrayList<>();
        for (Comment record : records) {
            Comments comments = new Comments();

            comments.setId(record.getId().toHexString());
            comments.setCreateDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(record.getCreated()), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy年MM月dd??HH:mm")));
            comments.setContent(record.getContent());

            for (UserInfo userInfo : userInfoList) {
                if(record.getUserId().longValue() == userInfo.getUserId().longValue()){
                    comments.setNickname(userInfo.getNickName());
                    comments.setAvatar(userInfo.getLogo());
                    break;
                }
            }

            String likeUserCommentKey = "QUANZI_COMMENT_LIKE_USER_" + user.getId() + "_" + comments.getId();
            comments.setHasLiked(this.redisTemplate.hasKey(likeUserCommentKey) ? 1 : 0); //是否点赞

            String likeCommentKey = "QUANZI_COMMENT_LIKE_" + comments.getId();
            String value = this.redisTemplate.opsForValue().get(likeCommentKey);
            if(StringUtils.isNotEmpty(value)){
                comments.setLikeCount(Integer.valueOf(value)); //点赞数
            }else{
                comments.setLikeCount(0); //点赞数
            }

            // 层级信息：回复评论带父评论 id 与被回复人昵称（旁路，失败仅置空不阻断）
            if (record.getParentId() != null) {
                comments.setParentId(record.getParentId().toHexString());
                try {
                    Comment parent = this.quanZiApi.queryCommentById(comments.getParentId());
                    if (parent != null && parent.getUserId() != null) {
                        UserInfo parentUser = this.userInfoApi.queryUserInfoById(parent.getUserId());
                        if (parentUser != null) {
                            comments.setReplyNickname(parentUser.getNickName());
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("查询被回复人信息失败：commentId={}", comments.getId());
                }
            }

            commentsList.add(comments);
        }

        pageResult.setItems(commentsList);

        return pageResult;
    }

    public Boolean saveComments(String publishId, String content, String replyCommentId) {
        User user = UserThreadLocal.get();
        boolean success;
        if (StringUtils.isNotBlank(replyCommentId)) {
            // 回复评论：由圈子服务归一到根评论后挂载
            success = this.quanZiApi.saveReplyComment(user.getId(), publishId, replyCommentId, content);
        } else {
            success = this.quanZiApi.saveComment(user.getId(), publishId, 2, content);
        }
        if (!success) {
            return false;
        }
        // 推送评论通知给动态作者（旁路：自己评论自己不推，失败仅日志不阻断）
        try {
            Publish publish = this.quanZiApi.queryPublishById(publishId);
            if (publish != null && publish.getUserId() != null
                    && !publish.getUserId().equals(user.getId())) {
                this.pushService.pushCommentNotification(publish.getUserId(), content);
            }
        } catch (Exception e) {
            LOGGER.warn("评论推送触发失败：publishId={}", publishId, e);
        }
        return true;
    }
}
