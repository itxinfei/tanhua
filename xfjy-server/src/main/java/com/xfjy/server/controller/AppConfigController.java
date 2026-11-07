package com.xfjy.server.controller;

import com.xfjy.server.service.SysConfigService;
import com.xfjy.server.vo.MapConfigVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端运行时配置下发。
 *
 * <p>挂在既有 {@code /xfjy/**} 网关前缀下，无需改网关路由。</p>
 */
@RestController
@RequestMapping("xfjy/config")
public class AppConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    /**
     * 地图服务配置（前端 JSAPI Key 与安全密钥）
     *
     * <p>由后台「系统配置 → 地图服务」维护，改完 30 秒内生效；未配置时返回空串，
     * 前端据此降级为「不支持地图选点」，而不是报网络错误。</p>
     */
    @GetMapping("map")
    public ResponseEntity<MapConfigVo> mapConfig() {
        return ResponseEntity.ok(this.sysConfigService.getMapConfig());
    }
}
