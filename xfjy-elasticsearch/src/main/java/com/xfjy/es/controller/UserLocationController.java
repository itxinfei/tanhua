package com.xfjy.es.controller;

import com.xfjy.es.pojo.UserLocationES;
import com.xfjy.es.service.UserLocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/es-user-location")
public class UserLocationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserLocationController.class);

    @Autowired
    private UserLocationService userLocationService;

    /**
     * 更新用户的地理位置
     */
    @PostMapping("/updateUserLocation")
    public ResponseEntity<Void> updateUserLocation(@RequestBody Map<String, Object> param) {
        try {
            Long userId = param.get("userId") == null ? null : Long.valueOf(param.get("userId").toString());
            Double longitude = param.get("longitude") == null ? null : Double.valueOf(param.get("longitude").toString());
            Double latitude = param.get("latitude") == null ? null : Double.valueOf(param.get("latitude").toString());
            String address = param.get("address") == null ? null : param.get("address").toString();
            if (userId == null || longitude == null || latitude == null) {
                return ResponseEntity.badRequest().build();
            }
            boolean result = this.userLocationService.updateUserLocation(userId, longitude, latitude, address);
            if (result) {
                return ResponseEntity.ok().build();
            }
        } catch (NumberFormatException e) {
            LOGGER.error("ES地理位置接口异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 查询用户的地理位置
     */
    @GetMapping("/queryByUserId")
    public ResponseEntity<UserLocationES> queryByUserId(@RequestParam("userId") Long userId) {
        try {
            UserLocationES userLocationES = this.userLocationService.queryByUserId(userId);
            if (null == userLocationES) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(userLocationES);
        } catch (Exception e) {
            LOGGER.error("ES地理位置接口异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 搜索附近的人
     */
    @GetMapping("/queryUserFromLocation")
    public ResponseEntity<List<UserLocationES>> queryUserFromLocation(@RequestParam("longitude") Double longitude,
                                                                       @RequestParam("latitude") Double latitude,
                                                                       @RequestParam(value = "range", defaultValue = "2000") Double range) {
        try {
            int page = 1;
            int pageSize = 100;
            Page<UserLocationES> userLocationES = this.userLocationService.queryUserFromLocation(longitude, latitude, range, page, pageSize);
            return ResponseEntity.ok(userLocationES.getContent());
        } catch (NumberFormatException | NullPointerException e) {
            LOGGER.error("ES地理位置接口异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}