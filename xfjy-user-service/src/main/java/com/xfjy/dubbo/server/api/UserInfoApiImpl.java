package com.xfjy.dubbo.server.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.dubbo.server.pojo.UserInfo;
import com.xfjy.dubbo.server.vo.UserInfoQueryParam;
import com.xfjy.user.mapper.UserInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @Cacheable(cacheNames = "userInfo", key = "#id")
    public UserInfo queryUserInfoById(Long id) {
        return this.userInfoMapper.selectById(id);
    }

    @Override
    @Cacheable(cacheNames = "userInfo", key = "#userId")
    public UserInfo queryByUserId(Long userId) {
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        return this.userInfoMapper.selectOne(queryWrapper);
    }

    @Override
    public List<UserInfo> queryUserInfoList(UserInfoQueryParam queryParam) {
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
        return this.userInfoMapper.selectList(queryWrapper);
    }

    @Override
    @CacheEvict(cacheNames = "userInfo", key = "#userInfo.userId")
    public Boolean updateUserInfoByUserId(UserInfo userInfo) {
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userInfo.getUserId());
        return this.userInfoMapper.update(userInfo, queryWrapper) > 0;
    }
}