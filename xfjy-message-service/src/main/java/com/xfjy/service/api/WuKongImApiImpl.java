package com.xfjy.service.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WuKongIM 开源 IM 集成契约实现（xfjy-message-service，18086）
 *
 * WuKongIM REST API 文档：https://wukong.mintlify.app/en/api/introduction.md
 * 鉴权模型：内部系统专用，无需 Token（区别于环信 app token 模型）
 * 端点映射（环信 → WuKongIM）：
 * - registerContact → POST /channel/subscriber_add（单聊频道双订阅）
 * - sendMessage     → POST /message/send（payload=Base64，透传 no_persist+sync_once）
 * - registerUser    → POST /user/token（uid+token 注册，幂等）
 * - getUserToken    → POST /user/token（注册后返回 token）
 * - getChatSessions → POST /conversation/sync（msg_count=1 组装摘要）
 * - getChatMessages → POST /channel/messagesync（按 seq 范围拉取）
 *
 * 单聊频道 ID 规则：channel_id = {较小uid}_{较大uid}，channel_type=1
 */
@Slf4j
@RestController
@RequestMapping("/api/wukongim")
public class WuKongImApiImpl implements WuKongImApi {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 单聊频道类型 */
    private static final int CHANNEL_TYPE_PERSONAL = 1;

    /** 系统发送方（透传消息 from_uid） */
    private static final String SYSTEM_UID = "system";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${wukongim.api-host:http://192.168.20.133:5001}")
    private String apiHost;

    // ==================== 契约方法 ====================

    /**
     * 好友关系注册到 WuKongIM
     *
     * POST /channel/subscriber_add
     * 单聊频道双方均加入订阅者，保证互相收发消息
     */
    @Override
    @PostMapping("/contacts/{userId}/{friendId}")
    public void registerContact(@PathVariable("userId") Long userId,
                                @PathVariable("friendId") Long friendId) {
        try {
            String channelId = personalChannelId(userId, friendId);
            Map<String, Object> body = new HashMap<>();
            body.put("channel_id", channelId);
            body.put("channel_type", CHANNEL_TYPE_PERSONAL);
            body.put("subscribers", List.of(String.valueOf(userId), String.valueOf(friendId)));
            body.put("reset", 0);
            body.put("temp_subscriber", 0);

            ResponseEntity<String> resp = post("/channel/subscriber_add", body);
            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("[WuKongIM] 好友注册成功: {} <-> {}, channel={}", userId, friendId, channelId);
            } else {
                log.warn("[WuKongIM] 好友注册失败: {} <-> {}, status={}, body={}",
                        userId, friendId, resp.getStatusCode(), resp.getBody());
            }
        } catch (Exception e) {
            log.error("[WuKongIM] 好友注册异常: {} <-> {}", userId, friendId, e);
        }
    }

    /**
     * 发送消息（透传语义：不持久化、只推在线）
     *
     * POST /message/send
     * from_uid=system, channel_id=target, payload=Base64(msg)
     * header: no_persist=1 + sync_once=1，等价环信透传消息（不存离线）
     */
    @Override
    @PostMapping("/messages")
    public void sendMessage(@RequestParam("target") String target,
                            @RequestParam("msg") String msg) {
        try {
            String payload = Base64.getEncoder().encodeToString(
                    msg.getBytes(StandardCharsets.UTF_8));

            Map<String, Object> header = new HashMap<>();
            header.put("no_persist", 1);
            header.put("red_dot", 0);
            header.put("sync_once", 1);

            Map<String, Object> body = new HashMap<>();
            body.put("from_uid", SYSTEM_UID);
            body.put("channel_id", target);
            body.put("channel_type", CHANNEL_TYPE_PERSONAL);
            body.put("payload", payload);
            body.put("header", header);

            ResponseEntity<String> resp = post("/message/send", body);
            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("[WuKongIM] 消息发送成功: -> {}", target);
            } else {
                log.warn("[WuKongIM] 消息发送失败: -> {}, status={}, body={}",
                        target, resp.getStatusCode(), resp.getBody());
            }
        } catch (Exception e) {
            log.error("[WuKongIM] 消息发送异常: -> {}", target, e);
        }
    }

    /**
     * 注册 WuKongIM 用户（uid+token 注册到 IM 服务器）
     *
     * POST /user/token
     * 幂等：重复注册同一 uid 仅覆盖 token，视为成功
     */
    @Override
    @PostMapping("/user-register")
    public boolean registerUser(@RequestParam("username") String username,
                                @RequestParam("password") String password) {
        try {
            ResponseEntity<String> resp = postUserToken(username, password);
            boolean ok = resp.getStatusCode().is2xxSuccessful();
            if (ok) {
                log.info("[WuKongIM] 用户注册成功: {}", username);
            } else {
                log.warn("[WuKongIM] 用户注册失败: {}, status={}, body={}",
                        username, resp.getStatusCode(), resp.getBody());
            }
            return ok;
        } catch (Exception e) {
            log.error("[WuKongIM] 用户注册异常: {}", username, e);
            return false;
        }
    }

    /**
     * 获取用户级 token（注册后返回，供 SDK 连接鉴权）
     *
     * POST /user/token
     * WuKongIM 模型：业务系统自定义 token，注册到 IM 服务器后原样返回
     */
    @Override
    @PostMapping("/user-token")
    public String getUserToken(@RequestParam("username") String username,
                               @RequestParam("password") String password) {
        try {
            ResponseEntity<String> resp = postUserToken(username, password);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("[WuKongIM] 获取用户token失败: {}, status={}", username, resp.getStatusCode());
                return null;
            }
            log.info("[WuKongIM] 获取用户token成功: {}", username);
            return password;
        } catch (Exception e) {
            log.error("[WuKongIM] 获取用户token异常: {}", username, e);
            return null;
        }
    }

    /**
     * 获取用户会话列表
     *
     * POST /conversation/sync
     * msg_count=1 取每个会话最后一条消息组装摘要
     */
    @Override
    @PostMapping("/chat-sessions")
    public List<Map<String, Object>> getChatSessions(@RequestParam("userId") String userId,
                                                      @RequestParam("limit") Integer limit) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("uid", userId);
            body.put("msg_count", 1);

            ResponseEntity<String> resp = post("/conversation/sync", body);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("[WuKongIM] 会话同步失败: {}, status={}", userId, resp.getStatusCode());
                return Collections.emptyList();
            }

            JsonNode conversations = MAPPER.readTree(resp.getBody());
            if (!conversations.isArray()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> sessions = new ArrayList<>();
            int count = 0;
            for (JsonNode conv : conversations) {
                if (count >= limit) break;

                String channelId = conv.path("channel_id").asString("");
                int channelType = conv.path("channel_type").asInt(0);
                String contactId = extractPeerUid(channelId, channelType, userId);
                if (contactId == null) continue;

                Map<String, Object> session = new HashMap<>();
                session.put("contactId", contactId);

                JsonNode recents = conv.path("recents");
                if (recents.isArray() && recents.size() > 0) {
                    JsonNode lastMsg = recents.get(0);
                    session.put("lastMessage", extractMessageText(lastMsg.path("payload").asString("")));
                    session.put("lastTime", lastMsg.path("timestamp").asLong(0L) * 1000L);
                } else {
                    session.put("lastMessage", "");
                    session.put("lastTime", 0L);
                }
                sessions.add(session);
                count++;
            }

            log.info("[WuKongIM] 会话列表查询成功: {}, 共{}条", userId, sessions.size());
            return sessions;
        } catch (Exception e) {
            log.error("[WuKongIM] 会话列表查询异常: {}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取两用户之间的聊天记录
     *
     * POST /channel/messagesync
     * cursor 作为 start_message_seq（数字字符串），返回最后一条 message_seq+1 作为下次游标
     * WuKongIM 按 seq 正序返回，无需 reverse
     */
    @Override
    @PostMapping("/chat-messages")
    public Map<String, Object> getChatMessages(@RequestParam("fromUserId") String fromUserId,
                                                @RequestParam("toUserId") String toUserId,
                                                @RequestParam("limit") Integer limit,
                                                @RequestParam(value = "cursor", required = false) String cursor) {
        Map<String, Object> result = new HashMap<>();
        try {
            String channelId = personalChannelId(Long.parseLong(fromUserId), Long.parseLong(toUserId));

            Map<String, Object> body = new HashMap<>();
            body.put("login_uid", fromUserId);
            body.put("channel_id", channelId);
            body.put("channel_type", CHANNEL_TYPE_PERSONAL);
            body.put("limit", limit);
            body.put("pull_mode", 0);
            if (cursor != null && !cursor.isEmpty()) {
                body.put("start_message_seq", Long.parseLong(cursor));
            }

            ResponseEntity<String> resp = post("/channel/messagesync", body);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("[WuKongIM] 聊天记录查询失败: {} -> {}, status={}",
                        fromUserId, toUserId, resp.getStatusCode());
                result.put("messages", Collections.emptyList());
                result.put("cursor", "");
                return result;
            }

            JsonNode messagesNode = MAPPER.readTree(resp.getBody());
            List<Map<String, Object>> messages = new ArrayList<>();
            long lastSeq = 0;
            if (messagesNode.isArray()) {
                for (JsonNode msg : messagesNode) {
                    // 解码 payload（业务侧结构化消息均封装为 JSON 文本，WuKongIM 以 text 类型存储）
                    JsonNode payload = decodePayload(msg.path("payload").asString(""));
                    Map<String, Object> m = new HashMap<>();
                    m.put("from", msg.path("from_uid").asString(""));
                    m.put("time", msg.path("timestamp").asLong(0L) * 1000L);
                    if (payload != null) {
                        String customType = payload.path("type").asString("");
                        switch (customType) {
                            case "image":
                                m.put("type", "image");
                                m.put("content", payload.path("url").asString(""));
                                break;
                            case "voice":
                                m.put("type", "voice");
                                m.put("content", payload.path("url").asString(""));
                                Map<String, Object> voiceExtra = new HashMap<>();
                                voiceExtra.put("duration", payload.path("duration").asInt(0));
                                m.put("extra", voiceExtra);
                                break;
                            case "location":
                                m.put("type", "location");
                                m.put("content", payload.path("address").asString(""));
                                Map<String, Object> locExtra = new HashMap<>();
                                locExtra.put("latitude", payload.path("latitude").asDouble(0));
                                locExtra.put("longitude", payload.path("longitude").asDouble(0));
                                m.put("extra", locExtra);
                                break;
                            case "card":
                                m.put("type", "card");
                                m.put("content", payload.path("nickname").asString(""));
                                Map<String, Object> cardExtra = new HashMap<>();
                                cardExtra.put("userId", payload.path("userId").asString(""));
                                cardExtra.put("avatar", payload.path("avatar").asString(""));
                                m.put("extra", cardExtra);
                                break;
                            default:
                                // 普通文本（WuKongIM type=1）或未知类型：content 取纯文本
                                m.put("type", "text");
                                m.put("content", payload.path("content").asString(""));
                        }
                    } else {
                        m.put("type", "text");
                        m.put("content", "");
                    }
                    messages.add(m);
                    lastSeq = msg.path("message_seq").asLong(lastSeq);
                }
            }

            String nextCursor = messages.isEmpty() ? "" : String.valueOf(lastSeq + 1);
            log.info("[WuKongIM] 聊天记录查询成功: {} -> {}, 共{}条", fromUserId, toUserId, messages.size());
            result.put("messages", messages);
            result.put("cursor", nextCursor);
            return result;
        } catch (Exception e) {
            log.error("[WuKongIM] 聊天记录查询异常: {} -> {}", fromUserId, toUserId, e);
            result.put("messages", Collections.emptyList());
            result.put("cursor", "");
            return result;
        }
    }

    /**
     * 今日消息数统计
     *
     * 通过 WuKongIM /message/statistics 获取今日消息总量
     * WuKongIM 不支持该端点时返回 null，前端显示「待接入」
     */
    @Override
    @GetMapping("/message-stats")
    public Long getTodayMessageCount() {
        try {
            Map<String, Object> body = new HashMap<>();
            // WuKongIM /message/statistics 需要 start_time/end_time 参数（毫秒时间戳）
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.atTime(23, 59, 59);
            body.put("start_time", startOfDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            body.put("end_time", endOfDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

            ResponseEntity<String> resp = post("/message/statistics", body);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("[WuKongIM] 消息统计失败: status={}", resp.getStatusCode());
                return null;
            }

            JsonNode node = MAPPER.readTree(resp.getBody());
            long count = node.path("count").asLong(0L);
            log.info("[WuKongIM] 今日消息统计: {}", count);
            return count;
        } catch (Exception e) {
            log.error("[WuKongIM] 消息统计异常", e);
            return null;
        }
    }

    // ==================== 内部工具 ====================

    /**
     * 注册/更新用户 token 到 WuKongIM
     *
     * POST /user/token
     * body: {uid, token, device_flag: 0=app, device_level: 1=主设备}
     */
    private ResponseEntity<String> postUserToken(String uid, String token) {
        Map<String, Object> body = new HashMap<>();
        body.put("uid", uid);
        body.put("token", token);
        body.put("device_flag", 0);
        body.put("device_level", 1);
        return post("/user/token", body);
    }

    /**
     * 构建单聊频道 ID：{较小uid}_{较大uid}
     * 保证两用户对同一频道 ID 达成一致
     */
    private String personalChannelId(Long uid1, Long uid2) {
        long min = Math.min(uid1, uid2);
        long max = Math.max(uid1, uid2);
        return min + "_" + max;
    }

    /**
     * 从单聊频道 ID 解析对方用户 ID
     *
     * channel_id 格式 {min}_{max}，去掉自己即为对方
     * 群聊频道（type=2）直接返回 channel_id 本身
     */
    private String extractPeerUid(String channelId, int channelType, String selfUid) {
        if (channelId == null || channelId.isEmpty()) return null;
        if (channelType != CHANNEL_TYPE_PERSONAL) return channelId;
        String[] parts = channelId.split("_");
        if (parts.length != 2) return channelId;
        if (parts[0].equals(selfUid)) return parts[1];
        if (parts[1].equals(selfUid)) return parts[0];
        return channelId;
    }

    /**
     * 从 payload（Base64 编码 JSON）提取消息文本
     *
     * WuKongIM 消息 payload 结构：{type: 1=text, content: "..."}
     * 非文本类型返回占位描述，与环信实现对齐
     */
    private String extractMessageText(String payloadBase64) {
        JsonNode payload = decodePayload(payloadBase64);
        if (payload == null) return "";
        int type = payload.path("type").asInt(1);
        switch (type) {
            case 1: return payload.path("content").asString("");
            case 2: return "[图片]";
            case 3: return "[表情]";
            case 4: return "[语音]";
            case 8: return "[文件]";
            default: return "[" + type + "]";
        }
    }

    /**
     * 从 payload 提取消息类型字符串（对齐环信 type 字段：text/image/audio...）
     */
    private String extractMessageType(String payloadBase64) {
        JsonNode payload = decodePayload(payloadBase64);
        if (payload == null) return "text";
        int type = payload.path("type").asInt(1);
        switch (type) {
            case 1: return "text";
            case 2: return "image";
            case 3: return "gif";
            case 4: return "audio";
            case 8: return "file";
            default: return "text";
        }
    }

    /**
     * Base64 解码 payload 为 JsonNode，失败返回 null
     */
    private JsonNode decodePayload(String payloadBase64) {
        if (payloadBase64 == null || payloadBase64.isEmpty()) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(payloadBase64);
            return MAPPER.readTree(new String(decoded, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("[WuKongIM] payload解码失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 发送 POST 请求到 WuKongIM API（无需鉴权头）
     */
    private ResponseEntity<String> post(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            String json = MAPPER.writeValueAsString(body);
            return restTemplate.postForEntity(apiHost + path,
                    new HttpEntity<>(json, headers), String.class);
        } catch (Exception e) {
            log.error("[WuKongIM] 请求失败: {}{}", apiHost, path, e);
            throw e;
        }
    }
}