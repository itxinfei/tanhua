package com.xfjy.service.api;

import com.xfjy.service.vo.UserLocationVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;
import java.util.Map;

/**
 * ES版用户地理位置API（原Dubbo version=2.0.0）
 */
@HttpExchange("/api/es-user-location")
public interface ESUserLocationApi {

    /**
     * 更新用户地理位置
     *
     * <p>服务端（xfjy-elasticsearch）用 {@code @RequestBody Map} 接收。HTTP Interface 中
     * 未标注注解的参数默认作为请求体传递，不能用 {@code @RequestParam}（那会把参数拼到
     * URL query string，服务端读不到 body 会返回 400 —— 历史 bug：上报位置一直失败）。
     */
    @PostExchange("/updateUserLocation")
    @PostMapping("/updateUserLocation")
    String updateUserLocation(Map<String, Object> param);

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