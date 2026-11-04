package com.xfjy.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.xfjy.service.api.ESUserLocationApi;
import com.xfjy.service.api.UserLocationApi;
import com.xfjy.server.pojo.User;
import com.xfjy.server.utils.UserThreadLocal;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class BaiduService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaiduService.class);

    @Autowired
    private UserLocationApi userLocationApi;

    @Autowired //ES版地理位置（附近的人查询走ES）
    private ESUserLocationApi esUserLocationApi;

    public Boolean updateLocation(Double longitude, Double latitude, String address) {
        User user = UserThreadLocal.get();
        // ES 优先写入：搜附近强依赖 ES，写入失败才整体失败
        try {
            Map<String, Object> param = new HashMap<>();
            param.put("userId", user.getId());
            param.put("longitude", longitude);
            param.put("latitude", latitude);
            param.put("address", address);
            this.esUserLocationApi.updateUserLocation(param);
        } catch (Exception e) {
            LOGGER.error("ES位置写入失败，搜附近将不可见: userId={}", user.getId(), e);
            return false;
        }
        // MongoDB 通用位置存储：失败不阻断主流程，仅记录告警（双写降级为单写）
        try {
            this.userLocationApi.updateUserLocation(user.getId(), longitude, latitude, address);
        } catch (Exception e) {
            LOGGER.warn("MongoDB位置写入失败（不影响搜附近）: userId={}", user.getId(), e);
        }
        return true;
    }

}