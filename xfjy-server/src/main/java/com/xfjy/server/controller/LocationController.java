package com.xfjy.server.controller;

import com.xfjy.server.response.Result;
import com.xfjy.server.service.LocationService;
import com.xfjy.server.vo.LocationVo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端定位辅助接口。
 *
 * <p>解决两个实际约束：</p>
 * <ol>
 *   <li>H5 在非 HTTPS 环境下浏览器禁用 Geolocation，需要 IP 兜底到城市；</li>
 *   <li>uni.chooseLocation 的地图 Key 为编译期配置，运营无法在后台调整，
 *       改为前端只负责取坐标，地址解析由服务端用后台维护的 Key 完成。</li>
 * </ol>
 */
@RestController
@RequestMapping("xfjy/location")
public class LocationController {

    @Autowired
    private LocationService locationService;

    /**
     * 经纬度逆地理（坐标 → 地址）
     *
     * @param param 仅使用 longitude / latitude 两个字段
     */
    @PostMapping("reverse-geocode")
    public ResponseEntity<Result<LocationVo>> reverseGeocode(@RequestBody LocationVo param) {
        return ResponseEntity.ok(Result.success(
                this.locationService.reverseGeocode(param.getLongitude(), param.getLatitude())));
    }

    /**
     * IP 兜底定位（设备定位不可用时落到城市级）
     */
    @GetMapping("ip-location")
    public ResponseEntity<Result<LocationVo>> ipLocation(HttpServletRequest request) {
        return ResponseEntity.ok(Result.success(
                this.locationService.ipLocation(resolveClientIp(request))));
    }

    /** 取真实客户端 IP：优先 X-Forwarded-For 首个地址，其次 X-Real-IP，最后直连地址 */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
