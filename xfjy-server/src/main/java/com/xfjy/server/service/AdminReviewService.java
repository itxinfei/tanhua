package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.server.mapper.ReviewMapper;
import com.xfjy.server.pojo.Review;
import com.xfjy.server.utils.AdminThreadLocal;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 后台内容审核服务
 */
@Service
public class AdminReviewService {

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private UserInfoApi userInfoApi;

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
        return this.reviewMapper.updateById(review) > 0;
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