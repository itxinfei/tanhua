package com.xfjy.server.service;

import com.xfjy.server.mapper.ReviewMapper;
import com.xfjy.server.pojo.Review;
import com.xfjy.server.vo.TextDetectResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 内容审核统一入口
 *
 * <p>文字走本地 DFA 敏感词引擎（{@link SensitiveWordService}）：
 * <ul>
 *   <li>BLOCK —— 命中 HIGH 级词，拒绝发布（调用方应返回 400）</li>
 *   <li>MANUAL —— 命中 MID 级词，内容放行 + 写入 tb_review 人工审核队列</li>
 *   <li>FILTERED —— 命中 LOW 级词，替换星号后放行</li>
 *   <li>PASS —— 未命中，原样放行</li>
 * </ul></p>
 *
 * <p>图片/视频当前无云端机审能力，统一写入 tb_review 人工审核队列（先发后审模式），
 * 管理端驳回时通过 targetId 联动下架。后期接入阿里云内容安全后，
 * 仅需在 {@link #cloudReview} 中实现并替换调用点，发布方代码不动。</p>
 */
@Service
public class ContentReviewService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentReviewService.class);

    /** 审核类型：动态（含图文） */
    public static final String TYPE_POST = "POST";
    /** 审核类型：图片 */
    public static final String TYPE_PHOTO = "PHOTO";
    /** 审核类型：视频 */
    public static final String TYPE_VIDEO = "VIDEO";

    @Autowired
    private SensitiveWordService sensitiveWordService;

    @Autowired
    private ReviewMapper reviewMapper;

    /**
     * 文字内容审核（本地敏感词引擎）
     *
     * @param text 待审核文本
     * @return 检测结果，调用方根据 action 决定：BLOCK→拒绝，MANUAL→放行+建审核单，FILTERED→用 filteredText 发布，PASS→原样发布
     */
    public TextDetectResult reviewText(String text) {
        return this.sensitiveWordService.checkText(text);
    }

    /**
     * 创建人工审核记录（先发后审：内容立即可见，管理端可驳回下架）
     *
     * @param type     审核类型 POST/PHOTO/VIDEO
     * @param userId   发布者用户ID
     * @param targetId 关联内容ID（动态 publishId / 视频 videoId）
     * @param content  文字内容摘要
     * @param medias   媒体URL，逗号分隔
     */
    public void createReviewRecord(String type, Long userId, String targetId, String content, String medias) {
        try {
            Review review = new Review();
            review.setType(type);
            review.setUserId(userId);
            review.setTargetId(targetId);
            review.setContent(content);
            review.setMedias(medias);
            review.setStatus("PENDING");
            this.reviewMapper.insert(review);
        } catch (Exception e) {
            // 审核记录写入失败不阻断发布主流程，仅记录日志（人工队列可事后补录）
            LOGGER.error("审核记录写入失败 type={} userId={} targetId={}", type, userId, targetId, e);
        }
    }

    /**
     * 云端内容审核（阿里云内容安全），预留扩展点
     *
     * <p>接入步骤：引入 alibabacloud-green20220302 SDK → 本方法调用图片/视频同步检测
     * → 返回 ReviewAction 替换本地引擎结果。发布方调用点无需改动。</p>
     *
     * @param text   文字内容
     * @param medias 媒体URL列表
     * @return TODO 接入阿里云后实现，当前返回 null 表示未启用云端审核
     */
    public TextDetectResult cloudReview(String text, String medias) {
        // TODO: 接入阿里云内容安全后实现（需 AccessKey 外置环境变量，不入库）
        return null;
    }
}