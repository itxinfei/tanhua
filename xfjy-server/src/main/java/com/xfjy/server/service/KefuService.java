package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.service.api.HuanXinApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.pojo.FAQ;
import com.xfjy.service.pojo.Feedback;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.server.mapper.FAQMapper;
import com.xfjy.server.mapper.FeedbackMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    @Autowired
    private UserInfoApi userInfoApi;

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
     * 获取客服环信登录凭证（admin 工作台专用）
     * 幂等注册客服账号后换取用户级 token，前端 SDK 用 username+token 登录
     *
     * @return {username, token}；环信不可用时 token 为 null
     */
    public Map<String, String> getAgentCredential() {
        String password = DigestUtils.md5Hex(KEFU_AGENT_ID + "_xfjy");
        Map<String, String> credential = new HashMap<>();
        credential.put("username", KEFU_AGENT_ID);
        try {
            this.huanXinApi.registerUser(KEFU_AGENT_ID, password);
            String token = this.huanXinApi.getUserToken(KEFU_AGENT_ID, password);
            credential.put("token", token);
        } catch (Exception e) {
            LOGGER.error("[客服] 获取客服凭证异常: {}", KEFU_AGENT_ID, e);
            credential.put("token", null);
        }
        return credential;
    }

    /**
     * 批量查询用户基础信息（admin 工作台会话列表展示昵称/头像）
     *
     * <p>环信 username 即 userId 字符串，工作台拿会话 conversationId 即可查询。</p>
     *
     * @param userIds 用户ID列表
     * @return [{userId, nickname, logo}] 列表，入参为空返回空列表
     */
    public List<Map<String, Object>> queryUsersInfo(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        UserInfoQueryParam queryParam = new UserInfoQueryParam();
        queryParam.setUserIds(userIds);
        List<UserInfo> userInfoList = this.userInfoApi.queryUserInfoList(queryParam);
        return userInfoList.stream().map(info -> {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", info.getUserId());
            map.put("nickname", info.getNickName());
            map.put("logo", info.getLogo());
            return map;
        }).collect(Collectors.toList());
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