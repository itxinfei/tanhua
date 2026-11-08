package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.service.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 后台网关限流配置 Controller
 *
 * <p>管理入口：后台「系统管理 → 限流配置」页。
 * 修改后双写：tb_sys_config（持久化，防 Redis 重启丢失）+ 网关专用 Redis key（实时生效）。
 * 网关本地缓存 30 秒，改配置约 30 秒内全节点生效，无需重启服务。</p>
 */
@RestController
@RequireRole("SUPER_ADMIN")
@RequestMapping("admin/rate-limit")
public class AdminRateLimitController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminRateLimitController.class);

    /** 默认限流参数（与网关硬编码兜底值一致） */
    private static final int DEFAULT_LIMIT = 5;
    private static final int DEFAULT_WINDOW_SECONDS = 60;

    /** tb_sys_config 配置键（持久化） */
    private static final String CONFIG_KEY_LIMIT = "gateway.ratelimit.limit";
    private static final String CONFIG_KEY_WINDOW = "gateway.ratelimit.window-seconds";

    /** 网关读取的 Redis 专用 key（实时生效） */
    private static final String REDIS_KEY_LIMIT = "xfjy:gateway:ratelimit:limit";
    private static final String REDIS_KEY_WINDOW = "xfjy:gateway:ratelimit:window-seconds";

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * GET /admin/rate-limit — 查询当前限流配置
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("limit", parseIntSafe(sysConfigService.getConfig(CONFIG_KEY_LIMIT, String.valueOf(DEFAULT_LIMIT)), DEFAULT_LIMIT));
        result.put("windowSeconds", parseIntSafe(sysConfigService.getConfig(CONFIG_KEY_WINDOW, String.valueOf(DEFAULT_WINDOW_SECONDS)), DEFAULT_WINDOW_SECONDS));
        result.put("defaultLimit", DEFAULT_LIMIT);
        result.put("defaultWindowSeconds", DEFAULT_WINDOW_SECONDS);
        return ResponseEntity.ok(result);
    }

    /**
     * PUT /admin/rate-limit — 更新限流配置
     *
     * <p>双写保证：tb_sys_config 持久化 + Redis 网关 key 实时写入。
     * 网关本地缓存 30 秒后感知新值。</p>
     *
     * @param body { limit: 1~100, windowSeconds: 10~3600 }
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        Integer limit = body.get("limit") == null ? null : parseIntSafe(body.get("limit").toString(), -1);
        Integer windowSeconds = body.get("windowSeconds") == null ? null : parseIntSafe(body.get("windowSeconds").toString(), -1);

        if (limit == null || limit < 1 || limit > 100) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "限流次数必须在 1~100 之间"));
        }
        if (windowSeconds == null || windowSeconds < 10 || windowSeconds > 3600) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "窗口秒数必须在 10~3600 之间"));
        }

        try {
            // 1. 持久化到 tb_sys_config（防 Redis 重启丢失）
            sysConfigService.setConfig(CONFIG_KEY_LIMIT, String.valueOf(limit), "网关IP限流：窗口内最大请求数");
            sysConfigService.setConfig(CONFIG_KEY_WINDOW, String.valueOf(windowSeconds), "网关IP限流：窗口大小（秒）");

            // 2. 同步写网关专用 Redis key（网关只读这两个 key，实时生效）
            stringRedisTemplate.opsForValue().set(REDIS_KEY_LIMIT, String.valueOf(limit));
            stringRedisTemplate.opsForValue().set(REDIS_KEY_WINDOW, String.valueOf(windowSeconds));

            LOGGER.info("[限流配置] 已更新: limit={}, windowSeconds={}s", limit, windowSeconds);
            return ResponseEntity.ok(Map.of("message", "限流配置已保存，约 30 秒内生效"));
        } catch (Exception e) {
            LOGGER.error("更新限流配置异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "保存限流配置失败"));
        }
    }

    private static int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}