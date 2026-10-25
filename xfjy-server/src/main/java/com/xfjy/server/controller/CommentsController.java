package com.xfjy.server.controller;

import com.xfjy.server.service.CommentsService;
import com.xfjy.server.service.ContentReviewService;
import com.xfjy.server.service.MovementsService;
import com.xfjy.server.service.RiskControlService;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.server.vo.PageResult;
import com.xfjy.server.vo.TextDetectResult;
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
     * @param publishId
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping
    public ResponseEntity<PageResult> queryCommentsList(@RequestParam("movementId") String publishId,
                                                        @RequestParam(value = "page", defaultValue = "1") Integer page,
                                                        @RequestParam(value = "pagesize", defaultValue = "10") Integer pageSize) {
        try {
            PageResult pageResult = this.commentsService.queryCommentsList(publishId, page, pageSize);
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

            // 风控：评论频率限制
            if (!this.riskControlService.allow(UserThreadLocal.get().getId(), RiskControlService.ACTION_COMMENT)) {
                return ResponseEntity.status(429).build();
            }

            // 审核：敏感词检测，BLOCK 直接拒发
            TextDetectResult detect = this.contentReviewService.reviewText(content);
            if ("BLOCK".equals(detect.getAction())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            Boolean bool = this.commentsService.saveComments(publishId, detect.getFilteredText());
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
