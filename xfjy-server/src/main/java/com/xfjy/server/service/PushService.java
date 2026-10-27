package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.server.mapper.UserDeviceMapper;
import com.xfjy.server.pojo.Settings;
import com.xfjy.server.pojo.UserDevice;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 极光推送服务（REST API v3/push 直调，不引入极光 Java SDK）
 *
 * 凭证通过环境变量外置（JPUSH_APP_KEY / JPUSH_MASTER_SECRET），
 * 留空时走 mock 模式（仅打日志不实际推送），便于本地开发联调。
 * 推送失败仅记录 warn 日志，不阻断主业务链路。
 */
@Service
public class PushService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushService.class);

    private static final String JPUSH_PUSH_URL = "https://api.jpush.cn/v3/push";

    @Value("${xfjy.jpush.app-key:}")
    private String appKey;

    @Value("${xfjy.jpush.master-secret:}")
    private String masterSecret;

    @Value("${xfjy.jpush.enabled:true}")
    private Boolean enabled;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private UserDeviceMapper userDeviceMapper;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 匹配成功推送（通知对方用户）
     *
     * @param targetUserId 被通知用户ID
     */
    public void pushMatchNotification(Long targetUserId) {
        if (!Boolean.TRUE.equals(enabled)) {
            return;
        }
        try {
            // 1. 校验通知开关（matchNotification 为 null 视为开启，与 SettingsVo 默认一致）
            Settings settings = this.settingsService.querySettingsByUserId(targetUserId);
            if (settings != null && Boolean.FALSE.equals(settings.getMatchNotification())) {
                return;
            }

            // 2. 查询目标用户绑定的设备
            List<UserDevice> devices = this.listDevicesByUserId(targetUserId);
            if (devices.isEmpty()) {
                LOGGER.debug("推送跳过：用户{}未绑定设备", targetUserId);
                return;
            }

            String alert = "你们互相喜欢了，快去聊聊吧！";
            String title = "匹配成功";
            List<String> registrationIds = devices.stream()
                    .map(UserDevice::getRegistrationId)
                    .collect(Collectors.toList());

            // 3. 凭证为空走 mock 模式（仅日志，便于本地联调）
            if (StringUtils.isEmpty(appKey) || StringUtils.isEmpty(masterSecret)) {
                LOGGER.info("[PushMock] 匹配成功推送 -> userId={}, rids={}, alert={}",
                        targetUserId, registrationIds, alert);
                return;
            }

            // 4. 调用极光 REST API
            this.doJPush(registrationIds, title, alert);
            LOGGER.info("匹配成功推送完成：userId={}", targetUserId);
        } catch (Exception e) {
            // 推送是旁路逻辑，任何异常不阻断主链路
            LOGGER.warn("匹配成功推送失败：userId={}", targetUserId, e);
        }
    }

    /**
     * 点赞通知（动态被点赞时通知作者）
     *
     * @param targetUserId 动态作者用户ID
     */
    public void pushLikeNotification(Long targetUserId) {
        if (!Boolean.TRUE.equals(enabled)) {
            return;
        }
        try {
            // 1. 校验通知开关（likeNotification 为 null 视为开启）
            Settings settings = this.settingsService.querySettingsByUserId(targetUserId);
            if (settings != null && Boolean.FALSE.equals(settings.getLikeNotification())) {
                return;
            }

            // 2. 查询目标用户绑定的设备
            List<UserDevice> devices = this.listDevicesByUserId(targetUserId);
            if (devices.isEmpty()) {
                LOGGER.debug("推送跳过：用户{}未绑定设备", targetUserId);
                return;
            }

            String title = "收到点赞";
            String alert = "有人赞了你的内容，快去看看吧！";
            List<String> registrationIds = devices.stream()
                    .map(UserDevice::getRegistrationId)
                    .collect(Collectors.toList());

            // 3. 凭证为空走 mock 模式
            if (StringUtils.isEmpty(appKey) || StringUtils.isEmpty(masterSecret)) {
                LOGGER.info("[PushMock] 点赞推送 -> userId={}, rids={}, alert={}",
                        targetUserId, registrationIds, alert);
                return;
            }

            // 4. 调用极光 REST API
            this.doJPush(registrationIds, title, alert);
            LOGGER.info("点赞推送完成：userId={}", targetUserId);
        } catch (Exception e) {
            LOGGER.warn("点赞推送失败：userId={}", targetUserId, e);
        }
    }

    /**
     * 评论通知（动态被评论时通知作者）
     *
     * @param targetUserId 动态作者用户ID
     * @param content      评论内容（超长截断）
     */
    public void pushCommentNotification(Long targetUserId, String content) {
        if (!Boolean.TRUE.equals(enabled)) {
            return;
        }
        try {
            // 1. 校验通知开关（pinglunNotification 为 null 视为开启）
            Settings settings = this.settingsService.querySettingsByUserId(targetUserId);
            if (settings != null && Boolean.FALSE.equals(settings.getPinglunNotification())) {
                return;
            }

            // 2. 查询目标用户绑定的设备
            List<UserDevice> devices = this.listDevicesByUserId(targetUserId);
            if (devices.isEmpty()) {
                LOGGER.debug("推送跳过：用户{}未绑定设备", targetUserId);
                return;
            }

            String title = "收到评论";
            String brief = StringUtils.length(content) > 20 ? content.substring(0, 20) + "..." : content;
            String alert = "收到新评论：" + brief;
            List<String> registrationIds = devices.stream()
                    .map(UserDevice::getRegistrationId)
                    .collect(Collectors.toList());

            // 3. 凭证为空走 mock 模式
            if (StringUtils.isEmpty(appKey) || StringUtils.isEmpty(masterSecret)) {
                LOGGER.info("[PushMock] 评论推送 -> userId={}, rids={}, alert={}",
                        targetUserId, registrationIds, alert);
                return;
            }

            // 4. 调用极光 REST API
            this.doJPush(registrationIds, title, alert);
            LOGGER.info("评论推送完成：userId={}", targetUserId);
        } catch (Exception e) {
            LOGGER.warn("评论推送失败：userId={}", targetUserId, e);
        }
    }

    /**
     * 公告广播（管理员发布公告时全量广播）
     *
     * <p>广播模式 audience=all，不查设备列表与个人开关：
     * 全量查开关+设备不现实，gonggaoNotification 开关由 app 内公告列表过滤。</p>
     *
     * @param title   公告标题
     * @param content 公告内容（超长截断）
     */
    public void pushNoticeBroadcast(String title, String content) {
        if (!Boolean.TRUE.equals(enabled)) {
            return;
        }
        try {
            String brief = StringUtils.length(content) > 40 ? content.substring(0, 40) + "..." : content;
            // 凭证为空走 mock 模式
            if (StringUtils.isEmpty(appKey) || StringUtils.isEmpty(masterSecret)) {
                LOGGER.info("[PushMock] 公告广播 -> title={}, alert={}", title, brief);
                return;
            }
            this.doJPushBroadcast(title, brief);
            LOGGER.info("公告广播完成：title={}", title);
        } catch (Exception e) {
            LOGGER.warn("公告广播失败：title={}", title, e);
        }
    }

    /**
     * 调用极光 v3/push 接口
     */
    private void doJPush(List<String> registrationIds, String title, String alert) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String auth = Base64.getEncoder()
                .encodeToString((appKey + ":" + masterSecret).getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + auth);

        Map<String, Object> body = new HashMap<>();
        body.put("platform", "all");
        Map<String, Object> audience = new HashMap<>();
        audience.put("registration_id", registrationIds);
        body.put("audience", audience);

        Map<String, Object> notification = new HashMap<>();
        notification.put("alert", alert);
        Map<String, Object> android = new HashMap<>();
        android.put("alert", alert);
        android.put("title", title);
        notification.put("android", android);
        Map<String, Object> ios = new HashMap<>();
        ios.put("alert", alert);
        ios.put("sound", "default");
        notification.put("ios", ios);
        body.put("notification", notification);

        Map<String, Object> options = new HashMap<>();
        options.put("apns_production", false); // 开发环境走 APNs sandbox
        body.put("options", options);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = this.restTemplate.postForEntity(JPUSH_PUSH_URL, entity, String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            LOGGER.warn("极光推送响应异常：status={}, body={}", resp.getStatusCode(), resp.getBody());
        }
    }

    /**
     * 调用极光 v3/push 广播接口（audience=all，推送给所有集成 SDK 的设备）
     */
    private void doJPushBroadcast(String title, String alert) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String auth = Base64.getEncoder()
                .encodeToString((appKey + ":" + masterSecret).getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + auth);

        Map<String, Object> body = new HashMap<>();
        body.put("platform", "all");
        body.put("audience", "all"); // 广播模式

        Map<String, Object> notification = new HashMap<>();
        notification.put("alert", alert);
        Map<String, Object> android = new HashMap<>();
        android.put("alert", alert);
        android.put("title", title);
        notification.put("android", android);
        Map<String, Object> ios = new HashMap<>();
        ios.put("alert", alert);
        ios.put("sound", "default");
        notification.put("ios", ios);
        body.put("notification", notification);

        Map<String, Object> options = new HashMap<>();
        options.put("apns_production", false);
        body.put("options", options);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = this.restTemplate.postForEntity(JPUSH_PUSH_URL, entity, String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            LOGGER.warn("极光广播响应异常：status={}, body={}", resp.getStatusCode(), resp.getBody());
        }
    }

    private List<UserDevice> listDevicesByUserId(Long userId) {
        QueryWrapper<UserDevice> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        return this.userDeviceMapper.selectList(queryWrapper);
    }
}