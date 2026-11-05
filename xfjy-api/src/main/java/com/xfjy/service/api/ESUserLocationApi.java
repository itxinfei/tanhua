package com.xfjy.service.api;

import com.xfjy.service.vo.UserLocationVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
     * <p>服务端（xfjy-elasticsearch）用 {@code @RequestBody Map} 接收。HTTP Interface 的
     * 参数必须显式标注 {@code @RequestBody} 才会作为请求体传递——未注解的参数没有合适的
     * {@code HttpServiceArgumentResolver}，运行期会抛
     * {@code IllegalStateException: Could not resolve parameter [0] ... No suitable resolver}；
     * 而 {@code @RequestParam} 会把参数拼到 URL query string，服务端读不到 body 返回 400。
     * （两者都导致上报位置一直失败，历史 bug）
     */
    @PostExchange("/updateUserLocation")
    @PostMapping("/updateUserLocation")
    // void：下游（match-service 实现）转发 ES 失败时返回 200 空 body（无 Content-Type），
    // 声明 String 会让 RestClient 抛 UnknownContentTypeException；void 不解析响应体，对空响应免疫
    void updateUserLocation(@RequestBody Map<String, Object> param);

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