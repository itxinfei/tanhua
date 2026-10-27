package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.service.pojo.FAQ;
import com.xfjy.service.pojo.Feedback;
import com.xfjy.server.mapper.FAQMapper;
import com.xfjy.server.mapper.FeedbackMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 客服服务（app 端）：FAQ 查询 + 意见反馈提交
 */
@Service
public class KefuService {

    @Autowired
    private FAQMapper faqMapper;

    @Autowired
    private FeedbackMapper feedbackMapper;

    /**
     * 查询启用中的 FAQ 列表（按 sort 升序）
     */
    public List<FAQ> listEnabledFaq() {
        QueryWrapper<FAQ> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        wrapper.orderByAsc("sort");
        return this.faqMapper.selectList(wrapper);
    }

    /**
     * 提交意见反馈
     *
     * @return 成功返回 true；内容为空返回 false
     */
    public Boolean submitFeedback(Long userId, String content, String contact) {
        if (userId == null || StringUtils.isBlank(content)) {
            return false;
        }
        Feedback entity = new Feedback();
        entity.setUserId(userId);
        entity.setContent(content);
        entity.setContact(StringUtils.isBlank(contact) ? null : contact);
        entity.setStatus(0);
        entity.setCreated(new Date());
        return this.feedbackMapper.insert(entity) > 0;
    }

    /**
     * 查询我的反馈列表（按提交时间倒序）
     * 返回 Map 列表，created 格式化为 createDate 字符串（yyyy-MM-dd HH:mm）
     */
    public List<Map<String, Object>> myFeedbackList(Long userId) {
        QueryWrapper<Feedback> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.orderByDesc("created");
        List<Feedback> list = this.feedbackMapper.selectList(wrapper);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return list.stream().map(item -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("content", item.getContent());
            map.put("contact", item.getContact());
            map.put("reply", item.getReply());
            map.put("status", item.getStatus());
            map.put("createDate", item.getCreated() == null ? null
                    : formatter.format(item.getCreated().toInstant().atZone(ZoneId.systemDefault())));
            return map;
        }).collect(java.util.stream.Collectors.toList());
    }
}