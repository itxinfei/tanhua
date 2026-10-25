package com.xfjy.server.controller;

import com.xfjy.server.service.ContentReviewService;
import com.xfjy.server.service.MovementsService;
import com.xfjy.server.service.RiskControlService;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.server.vo.Movements;
import com.xfjy.server.vo.PageResult;
import com.xfjy.server.vo.TextDetectResult;
import com.xfjy.server.vo.VisitorsVo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequestMapping("movements")
@RestController
public class MovementsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MovementsController.class);

    @Autowired
    private MovementsService movementsService;

    @Autowired
    private ContentReviewService contentReviewService;

    @Autowired
    private RiskControlService riskControlService;

    /**
     * 发布动态
     *
     * <p>风控：单用户 60s 内最多发布 5 次，超限 429</p>
     * <p>审核：文字走敏感词检测，HIGH 级拒发(400)，LOW 级替换星号，MID 级放行+进人工审核队列；
     * 含图片的动态统一进人工审核队列（先发后审，管理端可驳回下架）</p>
     *
     * @param textContent
     * @param location
     * @param longitude
     * @param latitude
     * @param multipartFile
     * @return
     */
    @PostMapping
    public ResponseEntity<Void> saveMovements(@RequestParam("textContent") String textContent,
                                              @RequestParam("location") String location,
                                              @RequestParam("longitude") String longitude,
                                              @RequestParam("latitude") String latitude,
                                              @RequestParam("imageContent") MultipartFile[] multipartFile) {
        try {
            Long userId = UserThreadLocal.get().getId();

            // 风控：发布频率限制
            if (!this.riskControlService.allow(userId, RiskControlService.ACTION_PUBLISH)) {
                return ResponseEntity.status(429).build();
            }

            // 审核：文字敏感词检测，BLOCK 直接拒发
            TextDetectResult detect = this.contentReviewService.reviewText(textContent);
            if ("BLOCK".equals(detect.getAction())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            String publishId = this.movementsService.saveMovements(detect.getFilteredText(), location, longitude, latitude, multipartFile);
            if (StringUtils.isNotEmpty(publishId)) {
                // MANUAL 或含图片：写入人工审核队列（先发后审）
                if ("MANUAL".equals(detect.getAction()) || (multipartFile != null && multipartFile.length > 0)) {
                    this.contentReviewService.createReviewRecord(
                            ContentReviewService.TYPE_POST, userId, publishId, detect.getFilteredText(), null);
                }
                return ResponseEntity.ok(null);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 查询好友的动态信息
     *
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping
    public ResponseEntity<PageResult> queryPublishList(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                       @RequestParam(value = "pagesize", defaultValue = "10") Integer pageSize) {
        try {
            PageResult pageResult = this.movementsService.queryUserPublishList(page, pageSize);
            return ResponseEntity.ok(pageResult);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 查询推荐的动态信息
     *
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping("recommend")
    public ResponseEntity<PageResult> queryRecommendPublishList(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                                @RequestParam(value = "pagesize", defaultValue = "10") Integer pageSize) {
        try {
            PageResult pageResult = this.movementsService.queryRecommendPublishList(page, pageSize);
            return ResponseEntity.ok(pageResult);
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
            if (!this.riskControlService.allow(UserThreadLocal.get().getId(), RiskControlService.ACTION_LIKE)) {
                return ResponseEntity.status(429).build();
            }
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

    /**
     * 喜欢
     *
     * @param publishId
     * @return
     */
    @GetMapping("/{id}/love")
    public ResponseEntity<Long> loveComment(@PathVariable("id") String publishId) {
        try {
            if (!this.riskControlService.allow(UserThreadLocal.get().getId(), RiskControlService.ACTION_LIKE)) {
                return ResponseEntity.status(429).build();
            }
            Long count = this.movementsService.loveComment(publishId);
            if (null != count) {

                return ResponseEntity.ok(count);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 取消喜欢
     *
     * @param publishId
     * @return
     */
    @GetMapping("/{id}/unlove")
    public ResponseEntity<Long> unLoveComment(@PathVariable("id") String publishId) {
        try {
            Long count = this.movementsService.unLoveComment(publishId);
            if (null != count) {

                return ResponseEntity.ok(count);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 查询单条动态信息
     *
     * @param publishId
     * @return
     */
    @GetMapping("{id}")
    public ResponseEntity<Movements> queryMovementsById(@PathVariable("id") String publishId){
        try {
            Movements movements = this.movementsService.queryMovementsById(publishId);
            if(null != movements){

                //记录浏览（评分事件 type=2，由圈子服务内部发送消息）
                this.movementsService.recordPublishView(publishId);

                return ResponseEntity.ok(movements);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 谁看过我
     *
     * @return
     */
    @GetMapping("visitors")
    public ResponseEntity<List<VisitorsVo>> queryVisitorsList(){
        try {
            List<VisitorsVo> list = this.movementsService.queryVisitorsList();
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 自己的所有动态
     *
     * @return
     */
    @GetMapping("all")
    public ResponseEntity<PageResult> queryAlbumList(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                     @RequestParam(value = "pagesize", defaultValue = "10") Integer pageSize,
                                                     @RequestParam(value = "userId") Long userId) {
        try {
            PageResult pageResult = this.movementsService.queryAlbumList(userId, page, pageSize);
            return ResponseEntity.ok(pageResult);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

}
