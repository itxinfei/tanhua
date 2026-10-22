package com.xfjy.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import com.xfjy.dubbo.server.api.UserLocationApi;
import com.xfjy.dubbo.server.pojo.UserLocation;
import com.xfjy.server.pojo.User;
import com.xfjy.server.utils.UserThreadLocal;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;

@Service
public class BaiduService {

    @Autowired
    private UserLocationApi userLocationApi;

    public Boolean updateLocation(Double longitude, Double latitude, String address) {
        try {
            User user = UserThreadLocal.get();
            this.userLocationApi.updateUserLocation(user.getId(), longitude, latitude, address);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

}
