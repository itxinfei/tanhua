package com.xfjy.service.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.service.enums.SexEnum;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.user.mapper.UserInfoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
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

    private static final Logger log = LoggerFactory.getLogger(UserInfoApiImpl.class);

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Override
    @GetMapping("/queryUserInfoById")
    public UserInfo queryUserInfoById(@RequestParam("id") Long id) {
        if (id == null) {
            return null;
        }
        // 契约语义是"按用户 id 查资料"，必须查 user_id 列（历史种子数据 id 与 user_id 恰好相等，
        // selectById 才一直没暴露问题；新注册用户 id 与 user_id 不再相等）
        UserInfo userInfo = selectByUserId(id);
        if (userInfo != null) {
            return userInfo;
        }
        // 懒初始化：SSO 验证码注册只写 tb_user，新用户首次查询时补建空资料行，
        // 同时自动修复历史缺失资料的账号
        return initBlankUserInfo(id);
    }

    /**
     * 按 user_id 查询单条资料
     */
    private UserInfo selectByUserId(Long userId) {
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        return this.userInfoMapper.selectOne(queryWrapper);
    }

    /**
     * 补建空资料行。显式写入 id=userId，保持项目"id 与 user_id 1:1"的历史约定；
     * 主键冲突同时充当并发去重：两个请求同时首查时后者重查即可。
     */
    private UserInfo initBlankUserInfo(Long userId) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        userInfo.setUserId(userId);
        userInfo.setSex(SexEnum.UNKNOWN);
        try {
            this.userInfoMapper.insert(userInfo);
            log.info("用户资料行不存在，已懒初始化, userId={}", userId);
            return userInfo;
        } catch (DuplicateKeyException e) {
            // 并发首查：另一线程已建行，重查返回
            UserInfo existing = selectByUserId(userId);
            if (existing != null) {
                return existing;
            }
            throw e;
        }
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
        if (userInfo.getUserId() == null) {
            return false;
        }
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userInfo.getUserId());
        if (this.userInfoMapper.selectCount(queryWrapper) > 0) {
            return this.userInfoMapper.update(userInfo, queryWrapper) > 0;
        }
        // upsert：资料行不存在（懒初始化之前的并发写入/历史缺失账号）时补建，
        // 否则新用户首次编辑资料 UPDATE 影响 0 行，资料永远无法保存
        if (userInfo.getId() == null) {
            userInfo.setId(userInfo.getUserId());
        }
        if (userInfo.getSex() == null) {
            userInfo.setSex(SexEnum.UNKNOWN);
        }
        try {
            return this.userInfoMapper.insert(userInfo) > 0;
        } catch (DuplicateKeyException e) {
            // 并发补建：另一线程已插入，退回按 user_id 更新
            return this.userInfoMapper.update(userInfo, queryWrapper) > 0;
        }
    }
}