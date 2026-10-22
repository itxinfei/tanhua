package com.xfjy.dubbo.server.api;

import com.xfjy.dubbo.server.vo.UserLocationVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

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

    /**
     * 查询用户地理位置
     */
    @GetExchange("/queryByUserId")
    @GetMapping("/queryByUserId")
    UserLocationVo queryByUserId(@RequestParam("userId") Long userId);

    /**
     * 根据地理位置查询用户
     */
    @GetExchange("/queryUserFromLocation")
    @GetMapping("/queryUserFromLocation")
    List<UserLocationVo> queryUserFromLocation(@RequestParam("longitude") Double longitude,
                                               @RequestParam("latitude") Double latitude,
                                               @RequestParam("range") Integer range);
}