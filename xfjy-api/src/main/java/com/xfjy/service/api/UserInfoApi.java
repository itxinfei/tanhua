package com.xfjy.service.api;

import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
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

    /**
     * 后台看板：人口属性聚合（服务端 SQL 聚合，避免 BFF 全量拉取 tb_user_info）
     *
     * 返回 {total, male, female, genderUnknown, ageRanges:[6段], ageCounts:[...],
     * cities:[{name,count}] 非空城市 TOP10}
     */
    @GetExchange("/admin/demographics")
    @GetMapping("/admin/demographics")
    Map<String, Object> adminDemographics();
}