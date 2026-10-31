package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.pojo.SysConfig;
import com.xfjy.server.service.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台系统配置管理 Controller
 *
 * 管理入口：后台「系统配置 → IM 通道」页
 * 切换 IM 通道后约 30 秒内全节点生效（Redis 缓存 TTL），无需重启服务
 */
@RestController
@RequireRole("ADMIN")
@RequestMapping("admin/sys-config")
public class SysConfigController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SysConfigController.class);

    @Autowired
    private SysConfigService sysConfigService;

    /**
     * 列出全部系统配置
     */
    @GetMapping
    public ResponseEntity<List<SysConfig>> listAll() {
        try {
            return ResponseEntity.ok(this.sysConfigService.listAll());
        } catch (Exception e) {
            LOGGER.error("查询系统配置异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 获取当前 IM 通道
     */
    @GetMapping("im-provider")
    public ResponseEntity<Map<String, String>> getImProvider() {
        Map<String, String> result = new HashMap<>();
        result.put("provider", this.sysConfigService.getImProvider());
        return ResponseEntity.ok(result);
    }

    /**
     * 切换 IM 通道
     *
     * @param param {provider: "wukongim" | "huanxin"}
     */
    @PutMapping("im-provider")
    public ResponseEntity<Map<String, Object>> switchImProvider(@RequestBody Map<String, String> param) {
        Map<String, Object> result = new HashMap<>();
        try {
            String provider = param.get("provider");
            if (!SysConfigService.IM_PROVIDER_WUKONGIM.equalsIgnoreCase(provider)
                    && !SysConfigService.IM_PROVIDER_HUANXIN.equalsIgnoreCase(provider)) {
                result.put("success", false);
                result.put("message", "provider 仅支持 wukongim / huanxin");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }

            this.sysConfigService.setConfig(
                    SysConfigService.KEY_IM_PROVIDER,
                    provider.toLowerCase(),
                    "IM 服务通道：wukongim=开源WuKongIM（默认），huanxin=环信第三方");

            result.put("success", true);
            result.put("provider", provider.toLowerCase());
            result.put("message", "切换成功，约30秒内全节点生效");
            LOGGER.info("IM通道已切换: {}", provider);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("切换IM通道异常", e);
            result.put("success", false);
            result.put("message", "切换失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}