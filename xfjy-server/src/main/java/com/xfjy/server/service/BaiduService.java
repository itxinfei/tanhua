package com.xfjy.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.xfjy.service.api.ESUserLocationApi;
import com.xfjy.service.api.UserLocationApi;
import com.xfjy.server.pojo.User;
import com.xfjy.server.utils.UserThreadLocal;
import org.springframework.stereotype.Service;

@Service
public class BaiduService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaiduService.class);

    @Autowired
    private UserLocationApi userLocationApi;

    @Autowired //ES版地理位置（附近的人查询走ES）
    private ESUserLocationApi esUserLocationApi;

    public Boolean updateLocation(Double longitude, Double latitude, String address) {
        try {
            User user = UserThreadLocal.get();
            // 双写：MongoDB（通用位置存储）+ ES（附近的人 Geo 查询）
            this.userLocationApi.updateUserLocation(user.getId(), longitude, latitude, address);
            this.esUserLocationApi.updateUserLocation(user.getId(), longitude, latitude, address);
            return true;
        } catch (Exception e) {
            LOGGER.error("更新位置失败", e);
        }
        return false;
    }

}
