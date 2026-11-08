package com.xfjy.server.controller;

import com.xfjy.server.service.CommentsService;
import com.xfjy.server.service.ContentReviewService;
import com.xfjy.server.service.MovementsService;
import com.xfjy.server.service.RiskControlService;
import com.xfjy.server.utils.ParamConvertUtils;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.server.vo.PageResult;
import com.xfjy.server.vo.TextDetectResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("comments")
public class CommentsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommentsController.class);

    @Autowired
    private CommentsService commentsService;

    @Autowired
    private MovementsService movementsService;

    @Autowired
    private ContentReviewService contentReviewService;

    @Autowired
    private RiskControlService riskControlService;

    /**
     * 查询评论列表
     *
     * <p>threaded=true 返回楼中楼结构（顶层评论分页 + 每条回复预览与回复总数）；
     * 默认 false 保持扁平列表，视频评论沿用旧语义。</p>
     *
     * @param publishId 动态ID
     * @param page      页码
     * @param pageSize  每页条数
     * @param threaded  是否楼中楼
     */
    @GetMapping
    public ResponseEntity<PageResult> queryCommentsList(@RequestParam("movementId") String publishId,
                                                        @RequestParam(value = "page", defaultValue = "1") Integer page,
                                                        @RequestParam(value = "pagesize", defaultValue = "10") Integer pageSize,
                                                        @RequestParam(value = "threaded", defaultValue = "false") Boolean threaded) {
        try {
            PageResult pageResult = this.commentsService.queryCommentsList(publishId, page,
                    ParamConvertUtils.capPageSize(pageSize, 10, 100), Boolean.TRUE.equals(threaded));
            if (null != pageResult) {
                return ResponseEntity.ok(pageResult);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 查询某条顶层评论下的全部回复（楼中楼「查看全部 N 条回复」）
     *
     * @param commentId 顶层评论id
     * @param page      页码
     * @param pageSize  每页条数
     */
    @GetMapping("/{id}/replies")
    public ResponseEntity<PageResult> queryCommentReplies(@PathVariable("id") String commentId,
                                                         @RequestParam(value = "page", defaultValue = "1") Integer page,
                                                         @RequestParam(value = "pagesize", defaultValue = "10") Integer pageSize) {
        try {
            PageResult pageResult = this.commentsService.queryCommentReplies(commentId, page,
                    ParamConvertUtils.capPageSize(pageSize, 10, 100));
            if (null != pageResult) {
                return ResponseEntity.ok(pageResult);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }


    /**
     * 发表评论
     *
     * <p>风控：单用户 60s 内最多评论 10 次，超限 429</p>
     * <p>审核：HIGH 级词拒发(400)，LOW 级词替换星号后发布；评论量大不建人工审核单，靠机审+举报兜底</p>
     *
     * @param param
     * @return
     */
    @PostMapping
    public ResponseEntity<Void> saveComments(@RequestBody Map<String, String> param) {
        try {
            String publishId = param.get("movementId");
            String content = param.get("comment");
            // 回复目标评论 id（可选，传了即为回复）
            String replyCommentId = param.get("commentId");
            // 语音评论字段（可选）：type=2 时 audioUrl/duration 必填
            Integer mediaType = param.get("type") != null ? Integer.valueOf(param.get("type")) : null;
            String audioUrl = param.get("audioUrl");
            Integer duration = param.get("duration") != null ? Integer.valueOf(param.get("duration")) : null;
            boolean isVoice = Integer.valueOf(2).equals(mediaType);

            // 语音评论校验：必须有音频地址与正数时长
            if (isVoice && (StringUtils.isBlank(audioUrl) || duration == null || duration <= 0)) {
                return ResponseEntity.badRequest().build();
            }

            // 风控：评论频率限制
            if (!this.riskControlService.allow(UserThreadLocal.get().getId(), RiskControlService.ACTION_COMMENT)) {
                return ResponseEntity.status(429).build();
            }

            // 审核：敏感词检测（语音评论 content 可为空，跳过文本机审；语音内容审核由 MinIO 侧/举报兜底）
            String filtered = content;
            if (StringUtils.isNotEmpty(content)) {
                TextDetectResult detect = this.contentReviewService.reviewText(content);
                if ("BLOCK".equals(detect.getAction())) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
                }
                filtered = detect.getFilteredText();
            }

            Boolean bool = this.commentsService.saveComments(publishId, filtered, replyCommentId,
                    isVoice ? 2 : null, audioUrl, duration);
            if (bool) {

                return ResponseEntity.ok(null);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 点赞
     *
     * @param publishId
     * @return
     */
    @GetMapping("/{id}/like")
    public ResponseEntity<Long> likeComment(@PathVariable("id") String publishId) {
        try {
            Long count = this.movementsService.likeComment(publishId);
            if (null != count) {
                return ResponseEntity.ok(count);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 取消点赞
     *
     * @param publishId
     * @return
     */
    @GetMapping("/{id}/dislike")
    public ResponseEntity<Long> disLikeComment(@PathVariable("id") String publishId) {
        try {
            Long count = this.movementsService.disLikeComment(publishId);
            if (null != count) {
                return ResponseEntity.ok(count);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

}
