package com.xfjy.dubbo.server.api;

import com.xfjy.dubbo.server.pojo.UserInfo;
import com.xfjy.dubbo.server.vo.UserInfoQueryParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 用户信息契约接口（tb_user_info 由 user-service 独占，跨服务只读走 HTTP）
 */
@HttpExchange("/api/userInfo")
public interface UserInfoApi {

    /**
     * 按主键查询用户信息（保持与原 UserInfoService.queryUserInfoById 一致语义：selectById）
     */
    @GetExchange("/queryUserInfoById")
    @GetMapping("/queryUserInfoById")
    UserInfo queryUserInfoById(@RequestParam("id") Long id);

    /**
     * 按 userId 查询单条用户信息
     */
    @GetExchange("/queryByUserId")
    @GetMapping("/queryByUserId")
    UserInfo queryByUserId(@RequestParam("userId") Long userId);

    /**
     * 条件查询用户信息列表（替代原 QueryWrapper 透传，条件均为可选）
     */
    @PostExchange("/queryUserInfoList")
    @PostMapping("/queryUserInfoList")
    List<UserInfo> queryUserInfoList(@RequestBody UserInfoQueryParam queryParam);

    /**
     * 根据 userId 更新用户信息
     */
    @PostExchange("/updateUserInfoByUserId")
    @PostMapping("/updateUserInfoByUserId")
    Boolean updateUserInfoByUserId(@RequestBody UserInfo userInfo);
}