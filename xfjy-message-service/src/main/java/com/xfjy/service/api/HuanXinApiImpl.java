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

import java.util.Collections;
import java.util.HashMap;
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
}