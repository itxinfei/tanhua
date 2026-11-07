package com.xfjy.server.controller;

import org.apache.commons.lang3.StringUtils;
import com.xfjy.server.service.ContentReviewService;
import com.xfjy.server.exception.BusinessException;
import com.xfjy.server.service.PushService;
import com.xfjy.server.service.RiskControlService;
import com.xfjy.server.service.VideoService;
import com.xfjy.server.utils.ParamConvertUtils;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.server.vo.PageResult;
import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.pojo.Comment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("small-videos")
public class VideoController {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoController.class);

    @Autowired
    private VideoService videoService;

    @Autowired
    private MovementsController movementsController;

    @Autowired
    private CommentsController commentsController;

    @Autowired
    private ContentReviewService contentReviewService;

    @Autowired
    private RiskControlService riskControlService;

    @Autowired
    private QuanZiApi quanZiApi;

    @Autowired
    private PushService pushService;

    /** 视频分页大小上限：防止超大分页参数拉爆列表接口（热点流候选池仅 100 条，50 已足够） */
    private static final int VIDEO_PAGE_MAX_SIZE = 50;

    /**
     * 解析分页大小：新契约 pageSize 优先；pagesize 仅作存量前端兼容入参（§5 存量豁免，随迁移移除）。
     */
    private static int resolvePageSize(Integer pageSize, Integer pageSizeLegacy) {
        Integer size = pageSize != null ? pageSize : pageSizeLegacy;
        return ParamConvertUtils.capPageSize(size == null ? 10 : size, 10, VIDEO_PAGE_MAX_SIZE);
    }

    /**
     * 发布小视频
     *
     * <p>风控：单用户 60s 内最多发布 5 次，超限 429</p>
     * <p>审核：视频统一进人工审核队列（先发后审，管理端可驳回下架）</p>
     */
    @PostMapping
    public ResponseEntity<Void> saveVideo(@RequestParam(value = "videoThumbnail", required = false) MultipartFile picFile,
                                          @RequestParam(value = "videoFile", required = false) MultipartFile videoFile,
                                          @RequestParam(value = "longitude", required = false) String longitude,
                                          @RequestParam(value = "latitude", required = false) String latitude,
                                          @RequestParam(value = "locationName", required = false) String locationName) {
        try {
            Long userId = UserThreadLocal.get().getId();

            // 风控：发布频率限制
            if (!this.riskControlService.allow(userId, RiskControlService.ACTION_PUBLISH)) {
                return ResponseEntity.status(429).build();
            }

            String id = this.videoService.saveVideo(picFile, videoFile, longitude, latitude, locationName);
            if (StringUtils.isNotEmpty(id)) {
                // 视频进人工审核队列（先发后审）
                this.contentReviewService.createReviewRecord(ContentReviewService.TYPE_VIDEO, userId, id, null, null);
                return ResponseEntity.ok(null);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 查询小视频列表
     *
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping
    public ResponseEntity<PageResult> queryVideoList(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                     @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                     @RequestParam(value = "pagesize", required = false) Integer pageSizeLegacy) {
        try {
            if (page <= 0) {
                page = 1;
            }
            PageResult pageResult = this.videoService.queryVideoList(page, resolvePageSize(pageSize, pageSizeLegacy));
            if (null != pageResult) {
                return ResponseEntity.ok(pageResult);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 查询「关注」流：仅返回当前用户关注的人发布的视频（分页）
     */
    @GetMapping("/focus")
    public ResponseEntity<PageResult> queryFocusVideoList(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                         @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                         @RequestParam(value = "pagesize", required = false) Integer pageSizeLegacy) {
        try {
            if (page <= 0) {
                page = 1;
            }
            PageResult pageResult = this.videoService.queryFollowVideoList(page, resolvePageSize(pageSize, pageSizeLegacy));
            if (null != pageResult) {
                return ResponseEntity.ok(pageResult);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 查询「热点」流：按点赞数排序的热门视频（分页）
     */
    @GetMapping("/hot")
    public ResponseEntity<PageResult> queryHotVideoList(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                       @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                       @RequestParam(value = "pagesize", required = false) Integer pageSizeLegacy) {
        try {
            if (page <= 0) {
                page = 1;
            }
            PageResult pageResult = this.videoService.queryHotVideoList(page, resolvePageSize(pageSize, pageSizeLegacy));
            if (null != pageResult) {
                return ResponseEntity.ok(pageResult);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 查询「定位」流：有地理位置信息的视频（分页）
     */
    @GetMapping("/location")
    public ResponseEntity<PageResult> queryLocationVideoList(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                             @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                             @RequestParam(value = "pagesize", required = false) Integer pageSizeLegacy,
                                                             @RequestParam(value = "lat", required = false) Double lat,
                                                             @RequestParam(value = "lng", required = false) Double lng) {
        try {
            if (page <= 0) {
                page = 1;
            }
            PageResult pageResult = this.videoService.queryLocationVideoList(
                    page, resolvePageSize(pageSize, pageSizeLegacy), lat, lng);
            if (null != pageResult) {
                return ResponseEntity.ok(pageResult);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 视频点赞
     *
     * @param videoId 视频id
     * @return
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<Long> likeComment(@PathVariable("id") String videoId) {
        // 先原子占位「已赞」标记：并发双击时只有一个请求翻转成功，落库 likeCount 只 +1。
        // 旧实现「先 hasLikedVideo 再改计数」存在读-改-写竞态，两次请求都可能读到未赞。
        boolean firstLike = this.videoService.tryMarkVideoLiked(videoId);
        ResponseEntity<Long> entity;
        try {
            entity = this.movementsController.likeComment(videoId);
        } catch (Exception e) {
            // 下游写入异常（Mongo/Redis 抖动）不再裸抛 500：撤销占位并给出可读错误，用户可重试
            LOGGER.error("视频点赞写入失败 videoId={}", videoId, e);
            if (firstLike) {
                this.videoService.rollbackVideoLiked(videoId);
            }
            throw new BusinessException("点赞失败，请稍后重试");
        }
        if (entity.getStatusCode().is2xxSuccessful()) {
            // 评分事件与计数属于旁路：失败不能把「已经点赞成功」的请求打成失败
            try {
                this.videoService.recordVideoMsg(videoId, 2);
                if (firstLike) {
                    this.videoService.incVideoLikeCount(videoId, 1);
                }
            } catch (Exception e) {
                LOGGER.warn("视频点赞埋点/计数失败 videoId={}", videoId, e);
            }
        } else if (firstLike) {
            // 下游失败：撤销占位，允许用户下次重试仍能正常 +1
            this.videoService.rollbackVideoLiked(videoId);
        }
        return entity;
    }

    /**
     * 取消点赞
     *
     * @param videoId
     * @return
     */
    @PostMapping("/{id}/dislike")
    public ResponseEntity<Long> disLikeComment(@PathVariable("id") String videoId) {
        // 原子释放「已赞」标记：只有真正由已赞翻转为未赞的那次请求才 -1，避免并发双击 -2。
        // 旧实现「先 hasLikedVideo 再减」同样有竞态。
        boolean firstUnlike = this.videoService.tryUnmarkVideoLiked(videoId);
        ResponseEntity<Long> entity;
        try {
            entity = this.movementsController.disLikeComment(videoId);
        } catch (Exception e) {
            LOGGER.error("视频取消点赞写入失败 videoId={}", videoId, e);
            if (firstUnlike) {
                this.videoService.restoreVideoLiked(videoId);
            }
            throw new BusinessException("取消点赞失败，请稍后重试");
        }

        if (entity.getStatusCode().is2xxSuccessful()) {
            // 评分事件与计数属于旁路：失败不能把「已经取消成功」的请求打成失败
            try {
                this.videoService.recordVideoMsg(videoId, 3);
                if (firstUnlike) {
                    this.videoService.incVideoLikeCount(videoId, -1);
                }
            } catch (Exception e) {
                LOGGER.warn("视频取消点赞埋点/计数失败 videoId={}", videoId, e);
            }
        } else if (firstUnlike) {
            // 下游失败：恢复标记，避免「标记没了但赞还在」
            this.videoService.restoreVideoLiked(videoId);
        }

        return entity;
    }

    /**
     * 播放上报（同一用户对同一视频只计一次，Redis 去重键 VIDEO_PLAY_DEDUP_{userId}_{videoId}）
     */
    @PostMapping("/{id}/play")
    public ResponseEntity<Void> reportPlay(@PathVariable("id") String videoId) {
        try {
            this.videoService.reportVideoPlay(videoId);
            return ResponseEntity.ok(null);
        } catch (Exception e) {
            LOGGER.error("播放上报异常 videoId={}", videoId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 评论列表
     */
    @GetMapping("/{id}/comments")
    public ResponseEntity<PageResult> queryCommentsList(@PathVariable("id") String videoId,
                                                        @RequestParam(value = "page", defaultValue = "1") Integer page,
                                                        @RequestParam(value = "pagesize", defaultValue = "10") Integer pagesize) {
        return this.commentsController.queryCommentsList(videoId, page, pagesize);
    }

    /**
     * 提交评论
     *
     * @param param
     * @param videoId
     * @return
     */
    @PostMapping("/{id}/comments")
    public ResponseEntity<Void> saveComments(@RequestBody Map<String, String> param,
                                             @PathVariable("id") String videoId) {
        param.put("movementId", videoId);

        ResponseEntity<Void> entity = this.commentsController.saveComments(param);

        if(entity.getStatusCode().is2xxSuccessful()){
            //视频评分事件：评论（type=4，由视频服务内部发送消息）
            this.videoService.recordVideoMsg(videoId, 4);
            // 评论数落库 +1（评论删除/隐藏时由管理端同步 -1）
            this.videoService.incVideoCommentCount(videoId, 1);
        }
        return entity;
    }

    /**
     * 评论点赞
     * 点赞成功后通知评论作者（旁路：自己赞自己不推，失败仅日志不阻断）
     *
     * @param commentId 评论id
     */
    @PostMapping("/comments/{id}/like")
    public ResponseEntity<Long> commentsLikeComment(@PathVariable("id") String commentId) {
        ResponseEntity<Long> entity = this.movementsController.likeComment(commentId);
        if (entity.getStatusCode().is2xxSuccessful() && entity.getBody() != null) {
            try {
                Comment comment = this.quanZiApi.queryCommentById(commentId);
                if (comment != null && comment.getUserId() != null) {
                    Long currentUserId = UserThreadLocal.get().getId();
                    if (!comment.getUserId().equals(currentUserId)) {
                        this.pushService.pushLikeNotification(comment.getUserId());
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("评论点赞推送触发失败：commentId={}", commentId, e);
            }
        }
        return entity;
    }

    /**
     * 评论取消点赞
     *
     * @param publishId
     * @return
     */
    @PostMapping("/comments/{id}/dislike")
    public ResponseEntity<Long> disCommentsLikeComment(@PathVariable("id") String publishId) {
        return this.movementsController.disLikeComment(publishId);
    }

    /**
     * 视频用户关注
     */
    @PostMapping("/{id}/userFocus")
    public ResponseEntity<Void> saveUserFocusComments(@PathVariable("id") Long userId) {
        try {
            Boolean bool = this.videoService.followUser(userId);
            if (bool) {
                return ResponseEntity.ok(null);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 取消视频用户关注
     */
    @PostMapping("/{id}/userUnFocus")
    public ResponseEntity<Void> saveUserUnFocusComments(@PathVariable("id") Long userId) {
        try {
            Boolean bool = this.videoService.disFollowUser(userId);
            if (bool) {
                return ResponseEntity.ok(null);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
