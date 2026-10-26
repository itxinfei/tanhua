package com.xfjy.service.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/user-location")
public interface UserLocationApi {

    /**
     * 更新用户地理位置
     */
    @PostExchange("/updateUserLocation")
    @PostMapping("/updateUserLocation")
    String updateUserLocation(@RequestParam("userId") Long userId,
                              @RequestParam("longitude") Double longitude,
                              @RequestParam("latitude") Double latitude,
                              @RequestParam("address") String address);

}