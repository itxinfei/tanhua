package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.api.VideoApi;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.server.mapper.ReviewMapper;
import com.xfjy.server.pojo.Review;
import com.xfjy.server.utils.AdminThreadLocal;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 后台内容审核服务
 */
@Service
public class AdminReviewService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminReviewService.class);

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private UserInfoApi userInfoApi;

    @Autowired
    private QuanZiApi quanZiApi;

    @Autowired
    private VideoApi videoApi;

    /**
     * 分页查询审核列表
     */
    public Map<String, Object> queryReviewPage(Integer page, Integer pageSize,
                                                String type, String status) {
        QueryWrapper<Review> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(type)) {
            wrapper.eq("type", type);
        }
        if (StringUtils.isNotBlank(status)) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created");

        IPage<Review> reviewPage = this.reviewMapper.selectPage(new Page<>(page, pageSize), wrapper);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Review review : reviewPage.getRecords()) {
            items.add(convertToItem(review));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", reviewPage.getTotal());
        result.put("list", items);
        return result;
    }

    /**
     * 通过审核
     */
    public Boolean approve(Long id) {
        Review review = this.reviewMapper.selectById(id);
        if (review == null || !"PENDING".equals(review.getStatus())) {
            return false;
        }
        review.setStatus("APPROVED");
        review.setReviewer(AdminThreadLocal.get() != null ? AdminThreadLocal.get().getNickname() : "admin");
        review.setReviewedAt(new Date());
        return this.reviewMapper.updateById(review) > 0;
    }

    /**
     * 驳回审核
     *
     * <p>驳回后联动下架：POST/PHOTO 删除动态（主表+相册+时间线），VIDEO 删除视频。
     * 下架失败不回滚审核状态，仅记录日志（内容可由管理端手动处理）。</p>
     */
    public Boolean reject(Long id, String reason) {
        Review review = this.reviewMapper.selectById(id);
        if (review == null || !"PENDING".equals(review.getStatus())) {
            return false;
        }
        review.setStatus("REJECTED");
        review.setRejectReason(reason);
        review.setReviewer(AdminThreadLocal.get() != null ? AdminThreadLocal.get().getNickname() : "admin");
        review.setReviewedAt(new Date());
        boolean updated = this.reviewMapper.updateById(review) > 0;

        if (updated && StringUtils.isNotEmpty(review.getTargetId())) {
            takedownContent(review);
        }
        return updated;
    }

    /**
     * 驳回时联动下架内容
     */
    private void takedownContent(Review review) {
        try {
            switch (review.getType()) {
                case "POST", "PHOTO" -> this.quanZiApi.removePublish(review.getTargetId());
                case "VIDEO" -> this.videoApi.removeVideo(review.getTargetId());
                default -> LOGGER.warn("未知审核类型，跳过下架 type={} reviewId={}", review.getType(), review.getId());
            }
        } catch (Exception e) {
            LOGGER.error("驳回联动下架失败 reviewId={} type={} targetId={}", review.getId(), review.getType(), review.getTargetId(), e);
        }
    }

    /**
     * 批量通过
     */
    public Integer batchApprove(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            if (this.approve(id)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 批量驳回
     */
    public Integer batchReject(List<Long> ids, String reason) {
        int count = 0;
        for (Long id : ids) {
            if (this.reject(id, reason)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Review对象转前端Item（合并用户昵称/头像）
     */
    private Map<String, Object> convertToItem(Review review) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", review.getId());
        item.put("type", review.getType());
        item.put("userId", review.getUserId());
        item.put("content", review.getContent());
        item.put("medias", review.getMedias() != null ? Arrays.asList(review.getMedias().split(",")) : Collections.emptyList());
        item.put("status", review.getStatus());
        item.put("submittedAt", review.getCreated());
        item.put("reviewer", review.getReviewer());
        item.put("reviewedAt", review.getReviewedAt());
        item.put("rejectReason", review.getRejectReason());

        // 查询用户信息
        UserInfo userInfo = this.userInfoApi.queryByUserId(review.getUserId());
        if (userInfo != null) {
            item.put("userNickname", userInfo.getNickName());
            item.put("userAvatar", userInfo.getLogo());
        } else {
            item.put("userNickname", "用户" + review.getUserId());
            item.put("userAvatar", "");
        }
        return item;
    }
}