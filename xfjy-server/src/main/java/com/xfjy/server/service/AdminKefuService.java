package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.service.pojo.FAQ;
import com.xfjy.service.pojo.Feedback;
import com.xfjy.server.mapper.FAQMapper;
import com.xfjy.server.mapper.FeedbackMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 后台客服管理服务：意见反馈处理 + FAQ 维护
 */
@Service
public class AdminKefuService {

    @Autowired
    private FeedbackMapper feedbackMapper;

    @Autowired
    private FAQMapper faqMapper;

    // ==================== 意见反馈 ====================

    /**
     * 分页查询反馈列表
     */
    public Map<String, Object> queryFeedbackPage(Integer page, Integer pageSize, Integer status) {
        QueryWrapper<Feedback> wrapper = new QueryWrapper<>();
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created");

        IPage<Feedback> feedbackPage = this.feedbackMapper.selectPage(new Page<>(page, pageSize), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", feedbackPage.getTotal());
        result.put("list", feedbackPage.getRecords());
        return result;
    }

    /**
     * 回复反馈（回复后状态置为已回复）
     */
    public Boolean replyFeedback(Long id, String reply) {
        if (StringUtils.isBlank(reply)) {
            return false;
        }
        Feedback entity = this.feedbackMapper.selectById(id);
        if (entity == null) {
            return false;
        }
        entity.setReply(reply);
        entity.setStatus(1);
        entity.setUpdated(new Date());
        return this.feedbackMapper.updateById(entity) > 0;
    }

    /**
     * 关闭反馈（不再处理）
     */
    public Boolean closeFeedback(Long id) {
        Feedback entity = this.feedbackMapper.selectById(id);
        if (entity == null) {
            return false;
        }
        entity.setStatus(2);
        entity.setUpdated(new Date());
        return this.feedbackMapper.updateById(entity) > 0;
    }

    // ==================== FAQ ====================

    /**
     * 分页查询 FAQ
     */
    public Map<String, Object> queryFaqPage(Integer page, Integer pageSize,
                                             String category, Integer status) {
        QueryWrapper<FAQ> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(category)) {
            wrapper.eq("category", category);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByAsc("sort");

        IPage<FAQ> faqPage = this.faqMapper.selectPage(new Page<>(page, pageSize), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", faqPage.getTotal());
        result.put("list", faqPage.getRecords());
        return result;
    }

    /**
     * 新增 FAQ（question 唯一，重复返回 false）
     */
    public Boolean createFaq(String category, String question, String answer, Integer sort) {
        if (StringUtils.isBlank(question) || StringUtils.isBlank(answer)) {
            return false;
        }
        QueryWrapper<FAQ> wrapper = new QueryWrapper<>();
        wrapper.eq("question", question);
        if (this.faqMapper.selectCount(wrapper) > 0) {
            return false;
        }
        FAQ entity = new FAQ();
        entity.setCategory(category);
        entity.setQuestion(question);
        entity.setAnswer(answer);
        entity.setSort(sort == null ? 0 : sort);
        entity.setStatus(1);
        return this.faqMapper.insert(entity) > 0;
    }

    /**
     * 编辑 FAQ（question 改动时查重排除自身）
     */
    public Boolean updateFaq(Long id, Map<String, Object> body) {
        FAQ entity = this.faqMapper.selectById(id);
        if (entity == null) {
            return false;
        }
        if (body.containsKey("category")) {
            entity.setCategory(body.get("category") == null ? null : String.valueOf(body.get("category")));
        }
        if (body.containsKey("question") && body.get("question") != null) {
            String question = String.valueOf(body.get("question"));
            if (!question.equals(entity.getQuestion())) {
                QueryWrapper<FAQ> wrapper = new QueryWrapper<>();
                wrapper.eq("question", question).ne("id", id);
                if (this.faqMapper.selectCount(wrapper) > 0) {
                    return false;
                }
            }
            entity.setQuestion(question);
        }
        if (body.containsKey("answer") && body.get("answer") != null) {
            entity.setAnswer(String.valueOf(body.get("answer")));
        }
        if (body.containsKey("sort") && body.get("sort") != null) {
            entity.setSort(Integer.valueOf(String.valueOf(body.get("sort"))));
        }
        return this.faqMapper.updateById(entity) > 0;
    }

    /**
     * FAQ 启用/停用
     */
    public Boolean toggleFaq(Long id, Integer status) {
        if (status == null) {
            return false;
        }
        FAQ entity = this.faqMapper.selectById(id);
        if (entity == null) {
            return false;
        }
        entity.setStatus(status);
        return this.faqMapper.updateById(entity) > 0;
    }

    /**
     * 删除 FAQ
     */
    public Boolean deleteFaq(Long id) {
        return this.faqMapper.deleteById(id) > 0;
    }
}