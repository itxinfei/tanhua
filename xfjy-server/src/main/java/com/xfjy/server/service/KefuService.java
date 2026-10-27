package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.service.api.HuanXinApi;
import com.xfjy.service.pojo.FAQ;
import com.xfjy.service.pojo.Feedback;
import com.xfjy.server.mapper.FAQMapper;
import com.xfjy.server.mapper.FeedbackMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 客服服务（app 端）：FAQ 查询 + 意见反馈提交 + 人工客服会话
 */
@Service
public class KefuService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KefuService.class);

    /** 固定客服环信账号（客服侧通过环信 Console 或后续工作台登录回复） */
    private static final String KEFU_AGENT_ID = "kefu0001";

    @Autowired
    private FAQMapper faqMapper;

    @Autowired
    private FeedbackMapper feedbackMapper;

    @Autowired
    private HuanXinApi huanXinApi;

    /**
     * 获取人工客服环信账号 ID
     * 首次调用时在环信侧幂等注册客服账号（密码规则与用户账号一致：md5(账号 + "_xfjy")）
     *
     * @return 客服环信账号 ID
     */
    public String getAgentId() {
        String password = DigestUtils.md5Hex(KEFU_AGENT_ID + "_xfjy");
        try {
            boolean registered = this.huanXinApi.registerUser(KEFU_AGENT_ID, password);
            if (registered) {
                LOGGER.info("[客服] 客服账号已就绪: {}", KEFU_AGENT_ID);
            } else {
                LOGGER.warn("[客服] 客服账号注册失败（环信不可用），仍返回账号ID: {}", KEFU_AGENT_ID);
            }
        } catch (Exception e) {
            LOGGER.error("[客服] 客服账号注册异常: {}", KEFU_AGENT_ID, e);
        }
        return KEFU_AGENT_ID;
    }

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