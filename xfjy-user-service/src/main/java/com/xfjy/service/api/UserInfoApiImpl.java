package com.xfjy.service.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.user.mapper.UserInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户信息契约实现（tb_user_info 由 user-service 独占）
 * 缓存语义与原 server 端 UserInfoService 保持一致
 */
@RestController
@RequestMapping("/api/userInfo")
public class UserInfoApiImpl implements UserInfoApi {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Override
    @GetMapping("/queryUserInfoById")
    public UserInfo queryUserInfoById(@RequestParam("id") Long id) {
        return this.userInfoMapper.selectById(id);
    }

    @Override
    @GetMapping("/queryByUserId")
    public UserInfo queryByUserId(@RequestParam("userId") Long userId) {
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        return this.userInfoMapper.selectOne(queryWrapper);
    }

    @Override
    @PostMapping("/queryUserInfoList")
    public List<UserInfo> queryUserInfoList(@RequestBody UserInfoQueryParam queryParam) {
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        if (queryParam.getUserIds() != null && !queryParam.getUserIds().isEmpty()) {
            queryWrapper.in("user_id", queryParam.getUserIds());
        }
        if (StringUtils.hasText(queryParam.getNickName())) {
            queryWrapper.like("nick_name", queryParam.getNickName());
        }
        if (queryParam.getSex() != null) {
            queryWrapper.eq("sex", queryParam.getSex());
        }
        if (queryParam.getAge() != null) {
            queryWrapper.eq("age", queryParam.getAge());
        }
        if (StringUtils.hasText(queryParam.getCity())) {
            queryWrapper.eq("city", queryParam.getCity());
        }
        return this.userInfoMapper.selectList(queryWrapper);
    }

    @Override
    @PostMapping("/updateUserInfoByUserId")
    @CacheEvict(cacheNames = "userInfo", key = "#userInfo.userId")
    public Boolean updateUserInfoByUserId(@RequestBody UserInfo userInfo) {
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userInfo.getUserId());
        return this.userInfoMapper.update(userInfo, queryWrapper) > 0;
    }
}