package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.server.mapper.UserInfoMapper;
import com.xfjy.server.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserInfoService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    /**
     * 查询数据库，查找用户的信息数据
     * 说明：为了简单处理，直接查询数据库了，建议：编写dubbo服务，进行调用
     *
     * @param id
     * @return
     */
    @Cacheable(cacheNames = "userInfo", key = "#id")
    public UserInfo queryUserInfoById(Long id) {
        return this.userInfoMapper.selectById(id);
    }

    /**
     * 查询用户信息列表
     *
     * @param queryWrapper
     * @return
     */
    public List<UserInfo> queryUserInfoList(QueryWrapper queryWrapper) {
        return this.userInfoMapper.selectList(queryWrapper);
    }

    @CacheEvict(cacheNames = "userInfo", key = "#userInfo.userId")
    public Boolean updateUserInfoByUserId(UserInfo userInfo) {
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userInfo.getUserId());
        return this.userInfoMapper.update(userInfo, queryWrapper) > 0;
    }
}
