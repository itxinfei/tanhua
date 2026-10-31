package com.xfjy.server.controller;

import com.xfjy.server.service.BaiduService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("baidu")
public class BaiduController {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaiduController.class);

    @Autowired
    private BaiduService baiduService;

    /**
     * 更新位置
     *
     * @param param
     * @return
     */
    @PostMapping("location")
    public ResponseEntity<Void> updateLocation(@RequestBody Map<String, Object> param) {
        try {
            Double longitude = Double.valueOf(param.get("longitude").toString());
            Double latitude = Double.valueOf(param.get("latitude").toString());
                        Object lonObj = param.get("longitude");
            Object latObj = param.get("latitude");
            Object addrObj = param.get("addrStr");
            if (lonObj == null || latObj == null || addrObj == null) {
                // 必填字段缺失（前端契约：longitude/latitude/addrStr）
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            Double longitude = Double.valueOf(lonObj.toString());
            Double latitude = Double.valueOf(latObj.toString());
            String address = addrObj.toString();

            Boolean bool = this.baiduService.updateLocation(longitude, latitude, address);
            if (bool) {
                return ResponseEntity.ok(null);
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("经纬度格式非法: {}", param, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
