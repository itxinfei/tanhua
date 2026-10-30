package com.xfjy.service.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 环信 IM 集成契约实现（xfjy-message-service，18086）
 *
 * 由原 xfjy-sso HuanXinController 迁出：
 * - 好友关系注册到环信（添加联系人时由 xfjy-server IMService 调用）
 * - 发送环信透传消息（回复陌生人问题等场景由 xfjy-server TodayBestService 调用）
 *
 * 环信 REST API 文档：https://docs.easemob.com/
 * appkey 格式 {org_name}#{app_name}，URL 路径为 {restHost}/{org_name}/{app_name}/...
 */
@Slf4j
@RestController
@RequestMapping("/api/huanxin")
public class HuanXinApiImpl implements HuanXinApi {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${huanxin.appkey:}")
    private String appkey;

    @Value("${huanxin.client-id:}")
    private String clientId;

    @Value("${huanxin.client-secret:}")
    private String clientSecret;

    @Value("${huanxin.rest-host:https://a1.easemob.com}")
    private String restHost;

    /**
     * 应用级 token 缓存（环信 app token 有效期约 60 天，避免每次请求重复获取）
     */
    private volatile String cachedToken;
    private volatile long tokenExpireAt;

    /**
     * 好友关系注册到环信
     */
    @Override
    @PostMapping("/contacts/{userId}/{friendId}")
    public void registerContact(@PathVariable("userId") Long userId,
                                @PathVariable("friendId") Long friendId) {
        try {
            String token = getAppToken();
            if (token == null) {
                log.warn("[环信] 获取token失败，跳过好友注册: {} -> {}", userId, friendId);
                return;
            }

            String url = buildUrl("users/" + userId + "/contacts/users/" + friendId);
            HttpHeaders headers = authHeader(token);

            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(headers), String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("[环信] 好友注册成功: {} -> {}", userId, friendId);
            } else {
                log.warn("[环信] 好友注册失败: {} -> {}, status={}", userId, friendId, resp.getStatusCode());
            }
        } catch (Exception e) {
            log.error("[环信] 好友注册异常: {} -> {}", userId, friendId, e);
        }
    }

    /**
     * 发送环信透传消息
     */
    @Override
    @PostMapping("/messages")
    public void sendMessage(@RequestParam("target") String target,
                            @RequestParam("msg") String msg) {
        try {
            String token = getAppToken();
            if (token == null) {
                log.warn("[环信] 获取token失败，跳过消息发送 -> {}", target);
                return;
            }

            String url = buildUrl("messages/users");
            HttpHeaders headers = authHeader(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 环信消息体：target列表 + msg（调用方传入的JSON透传内容）
            Map<String, Object> body = new HashMap<>();
            body.put("target", Collections.singletonList(target));
            body.put("msg", MAPPER.readTree(msg));

            ResponseEntity<String> resp = restTemplate.postForEntity(url,
                    new HttpEntity<>(MAPPER.writeValueAsString(body), headers), String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("[环信] 消息发送成功 -> {}", target);
            } else {
                log.warn("[环信] 消息发送失败 -> {}, status={}", target, resp.getStatusCode());
            }
        } catch (Exception e) {
            log.error("[环信] 消息发送异常 -> {}", target, e);
        }
    }

    /**
     * 注册环信用户
     * POST {restHost}/{org}/{app}/users，需应用级 token 鉴权
     * body: { "username": ..., "password": ... }
     * 幂等：400 且响应含 duplicate/exists/unique 视为用户已存在，返回 true
     */
    @Override
    @PostMapping("/user-register")
    public boolean registerUser(@RequestParam("username") String username,
                                @RequestParam("password") String password) {
        try {
            String token = getAppToken();
            if (token == null) {
                log.warn("[环信] 获取token失败，跳过用户注册: {}", username);
                return false;
            }

            String url = buildUrl("users");
            HttpHeaders headers = authHeader(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("username", username);
            body.put("password", password);

            ResponseEntity<String> resp = restTemplate.postForEntity(url,
                    new HttpEntity<>(MAPPER.writeValueAsString(body), headers), String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("[环信] 用户注册成功: {}", username);
                return true;
            }

            // 400 且含 duplicate/exists/unique → 用户已存在，幂等视为成功
            String respBody = resp.getBody();
            if (resp.getStatusCode().value() == 400 && respBody != null) {
                String lower = respBody.toLowerCase();
                if (lower.contains("duplicate") || lower.contains("exists") || lower.contains("unique")) {
                    log.info("[环信] 用户已存在，视为注册成功: {}", username);
                    return true;
                }
            }
            log.warn("[环信] 用户注册失败: {}, status={}, body={}", username, resp.getStatusCode(), respBody);
            return false;
        } catch (Exception e) {
            log.error("[环信] 用户注册异常: {}", username, e);
            return false;
        }
    }

    /**
     * 获取环信用户级 token（grant_type=password 模式）
     * 供前端 IM SDK 5.x login({userId, token}) 使用
     *
     * POST {restHost}/{org}/{app}/token
     * body: { "grant_type": "password", "username": ..., "password": ... }
     */
    @Override
    @PostMapping("/user-token")
    public String getUserToken(@RequestParam("username") String username,
                               @RequestParam("password") String password) {
        try {
            String url = buildUrl("token");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("grant_type", "password");
            body.put("username", username);
            body.put("password", password);

            ResponseEntity<String> resp = restTemplate.postForEntity(url,
                    new HttpEntity<>(MAPPER.writeValueAsString(body), headers), String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.error("[环信] 获取用户token失败, username={}, status={}", username, resp.getStatusCode());
                return null;
            }

            JsonNode node = MAPPER.readTree(resp.getBody());
            String accessToken = node.path("access_token").asString(null);
            if (accessToken == null) {
                log.error("[环信] 用户token响应缺少access_token: {}", resp.getBody());
                return null;
            }
            log.info("[环信] 获取用户token成功, username={}", username);
            return accessToken;
        } catch (Exception e) {
            log.error("[环信] 获取用户token异常, username={}", username, e);
            return null;
        }
    }

    /**
     * 获取环信应用级 token（client_credentials 模式），带缓存
     *
     * POST {restHost}/{org}/{app}/token
     * body: { "grant_type": "client_credentials", "client_id": ..., "client_secret": ... }
     */
    private String getAppToken() {
        // 缓存未过期直接返回（提前 5 分钟刷新）
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireAt - 300_000L) {
            return cachedToken;
        }

        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            log.warn("[环信] client-id/client-secret 未配置，无法获取token");
            return null;
        }

        try {
            String url = buildUrl("token");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("grant_type", "client_credentials");
            body.put("client_id", clientId);
            body.put("client_secret", clientSecret);

            ResponseEntity<String> resp = restTemplate.postForEntity(url,
                    new HttpEntity<>(MAPPER.writeValueAsString(body), headers), String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.error("[环信] 获取token失败, status={}", resp.getStatusCode());
                return null;
            }

            JsonNode node = MAPPER.readTree(resp.getBody());
            String accessToken = node.path("access_token").asString(null);
            long expiresIn = node.path("expires_in").asLong(0L);

            if (accessToken == null || expiresIn <= 0) {
                log.error("[环信] token响应缺少access_token/expires_in: {}", resp.getBody());
                return null;
            }

            cachedToken = accessToken;
            tokenExpireAt = System.currentTimeMillis() + expiresIn * 1000L;
            log.info("[环信] 获取token成功, 有效期={}秒", expiresIn);
            return cachedToken;
        } catch (Exception e) {
            log.error("[环信] 获取token异常", e);
            return null;
        }
    }

    /**
     * 构建环信 REST API URL：{restHost}/{org_name}/{app_name}/{path}
     * appkey 格式 {org_name}#{app_name}
     */
    private String buildUrl(String path) {
        String org = "";
        String app = "";
        if (appkey != null && appkey.contains("#")) {
            String[] parts = appkey.split("#", 2);
            org = parts[0];
            app = parts[1];
        }
        return restHost + "/" + org + "/" + app + "/" + path;
    }

    private HttpHeaders authHeader(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    /**
     * 获取用户会话列表
     *
     * 先拉取用户联系人列表，再对每个联系人拉取最后一条消息组装会话摘要。
     * 环信 REST API: GET /{org}/{app}/users/{username}/contacts
     * 环信 REST API: GET /{org}/{app}/chatmessages/{from}/{to}?limit=1
     */
    @Override
    @PostMapping("/chat-sessions")
    public List<Map<String, Object>> getChatSessions(@RequestParam("userId") String userId,
                                                      @RequestParam("limit") Integer limit) {
        try {
            String token = getAppToken();
            if (token == null) {
                log.warn("[环信] 获取token失败，无法查询会话列表: {}", userId);
                return Collections.emptyList();
            }

            // 1. 拉取联系人列表
            String contactsUrl = buildUrl("users/" + userId + "/contacts");
            HttpHeaders listHeaders = authHeader(token);
            ResponseEntity<String> contactsResp = restTemplate.exchange(contactsUrl, HttpMethod.GET,
                    new HttpEntity<>(listHeaders), String.class);
            if (!contactsResp.getStatusCode().is2xxSuccessful() || contactsResp.getBody() == null) {
                log.warn("[环信] 获取联系人列表失败: {}, status={}", userId, contactsResp.getStatusCode());
                return Collections.emptyList();
            }

            JsonNode contactsNode = MAPPER.readTree(contactsResp.getBody());
            JsonNode entriesNode = contactsNode.path("entries");
            if (!entriesNode.isArray()) {
                return Collections.emptyList();
            }

            // 2. 对每个联系人拉取最后一条消息（最多 limit 个）
            List<Map<String, Object>> sessions = new ArrayList<>();
            int count = 0;
            for (JsonNode contact : entriesNode) {
                if (count >= limit) break;
                String contactId = contact.path("username").asString(null);
                if (contactId == null || contactId.isEmpty()) continue;

                try {
                    String msgUrl = buildUrl("chatmessages/" + userId + "/" + contactId + "?limit=1");
                    ResponseEntity<String> msgResp = restTemplate.exchange(msgUrl, HttpMethod.GET,
                            new HttpEntity<>(authHeader(token)), String.class);
                    if (msgResp.getStatusCode().is2xxSuccessful() && msgResp.getBody() != null) {
                        JsonNode msgNode = MAPPER.readTree(msgResp.getBody());
                        JsonNode msgEntries = msgNode.path("entries");
                        if (msgEntries.isArray() && msgEntries.size() > 0) {
                            JsonNode lastMsg = msgEntries.get(0);
                            Map<String, Object> session = new HashMap<>();
                            session.put("contactId", contactId);
                            session.put("lastMessage", extractMessageText(lastMsg));
                            session.put("lastTime", lastMsg.path("timestamp").asLong(0L));
                            sessions.add(session);
                            count++;
                        } else {
                            // 无聊天记录的联系人也展示
                            Map<String, Object> session = new HashMap<>();
                            session.put("contactId", contactId);
                            session.put("lastMessage", "");
                            session.put("lastTime", 0L);
                            sessions.add(session);
                            count++;
                        }
                    }
                } catch (Exception e) {
                    log.warn("[环信] 获取与{}的最后消息失败: {}", contactId, e.getMessage());
                }
            }

            log.info("[环信] 查询会话列表成功: {}, 共{}条", userId, sessions.size());
            return sessions;
        } catch (Exception e) {
            log.error("[环信] 查询会话列表异常: {}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取两用户之间的聊天记录
     *
     * 环信 REST API: GET /{org}/{app}/chatmessages/{from}/{to}?limit=N&cursor=...
     * 返回格式: {entries: [...], cursor: "..."}
     */
    @Override
    @PostMapping("/chat-messages")
    public Map<String, Object> getChatMessages(@RequestParam("fromUserId") String fromUserId,
                                                @RequestParam("toUserId") String toUserId,
                                                @RequestParam("limit") Integer limit,
                                                @RequestParam(value = "cursor", required = false) String cursor) {
        Map<String, Object> result = new HashMap<>();
        try {
            String token = getAppToken();
            if (token == null) {
                log.warn("[环信] 获取token失败，无法查询聊天记录: {} -> {}", fromUserId, toUserId);
                result.put("messages", Collections.emptyList());
                result.put("cursor", "");
                return result;
            }

            StringBuilder url = new StringBuilder(buildUrl("chatmessages/" + fromUserId + "/" + toUserId));
            url.append("?limit=").append(limit);
            if (cursor != null && !cursor.isEmpty()) {
                url.append("&cursor=").append(cursor);
            }

            ResponseEntity<String> resp = restTemplate.exchange(url.toString(), HttpMethod.GET,
                    new HttpEntity<>(authHeader(token)), String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("[环信] 查询聊天记录失败: {} -> {}, status={}", fromUserId, toUserId, resp.getStatusCode());
                result.put("messages", Collections.emptyList());
                result.put("cursor", "");
                return result;
            }

            JsonNode node = MAPPER.readTree(resp.getBody());
            JsonNode entries = node.path("entries");
            List<Map<String, Object>> messages = new ArrayList<>();
            if (entries.isArray()) {
                for (JsonNode msg : entries) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("from", msg.path("from").asString(""));
                    m.put("content", extractMessageText(msg));
                    m.put("time", msg.path("timestamp").asLong(0L));
                    m.put("type", msg.path("type").asString("text"));
                    messages.add(m);
                }
                // 环信返回的是按时间倒序，前端需要正序
                Collections.reverse(messages);
            }

            String nextCursor = node.path("cursor").asString("");
            log.info("[环信] 查询聊天记录成功: {} -> {}, 共{}条", fromUserId, toUserId, messages.size());
            result.put("messages", messages);
            result.put("cursor", nextCursor);
            return result;
        } catch (Exception e) {
            log.error("[环信] 查询聊天记录异常: {} -> {}", fromUserId, toUserId, e);
            result.put("messages", Collections.emptyList());
            result.put("cursor", "");
            return result;
        }
    }

    /**
     * 从环信消息节点提取文本内容
     *
     * text 类型直接取 content；image/video 等类型返回占位描述
     */
    private String extractMessageText(JsonNode msgNode) {
        String type = msgNode.path("type").asString("text");
        if ("text".equals(type)) {
            return msgNode.path("content").asString("");
        } else if ("image".equals(type)) {
            return "[图片]";
        } else if ("video".equals(type)) {
            return "[视频]";
        } else if ("audio".equals(type)) {
            return "[语音]";
        } else {
            return "[" + type + "]";
        }
    }
}