package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.dubbo.server.api.UserInfoApi;
import com.xfjy.dubbo.server.pojo.UserInfo;
import com.xfjy.dubbo.server.vo.UserInfoQueryParam;
import com.xfjy.server.mapper.UserMapper;
import com.xfjy.server.pojo.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台用户管理服务
 */
@Service
public class AdminUserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserInfoApi userInfoApi;

    /**
     * 分页查询用户列表
     */
    public Map<String, Object> queryUserPage(Integer page, Integer pageSize,
                                              String keyword, String gender, String status) {
        QueryWrapper<User> userWrapper = new QueryWrapper<>();
        // 根据性别筛选：先查出符合条件的用户ID集合
        if (StringUtils.isNotBlank(gender)) {
            Integer sexValue = "男".equals(gender) ? 1 : 2; //SexEnum: MAN=1, WOMAN=2
            UserInfoQueryParam genderParam = new UserInfoQueryParam();
            genderParam.setSex(sexValue);
            List<UserInfo> infoList = this.userInfoApi.queryUserInfoList(genderParam);
            List<Long> userIds = infoList.stream().map(UserInfo::getUserId).collect(java.util.stream.Collectors.toList());
            if (userIds.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("total", 0L);
                empty.put("list", new ArrayList<>());
                return empty;
            }
            userWrapper.in("id", userIds);
        }

        // 查询用户基本信息
        if (StringUtils.isNotBlank(status)) {
            userWrapper.eq("status", status);
        }
        if (StringUtils.isNotBlank(keyword)) {
            userWrapper.like("mobile", keyword);
        }
        userWrapper.orderByDesc("created");

        IPage<User> userPage = this.userMapper.selectPage(new Page<>(page, pageSize), userWrapper);

        // 组装返回数据（合并UserInfo）
        List<Map<String, Object>> items = new ArrayList<>();
        for (User user : userPage.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("mobile", user.getMobile());
            item.put("status", user.getStatus() != null ? user.getStatus() : "NORMAL");
            item.put("freezeReason", user.getFreezeReason());
            item.put("createdAt", user.getCreated());
            item.put("lastActiveAt", user.getUpdated());

            // 查询用户详情
            UserInfo userInfo = this.userInfoApi.queryByUserId(user.getId());
            if (userInfo != null) {
                item.put("nickname", userInfo.getNickName());
                item.put("avatar", userInfo.getLogo());
                item.put("gender", userInfo.getSex() != null ? userInfo.getSex().toString() : "未知");
                item.put("age", userInfo.getAge());
                item.put("city", userInfo.getCity());
                item.put("vipLevel", 0); //VIP功能待实现
            } else {
                item.put("nickname", "用户" + user.getId());
                item.put("avatar", "");
                item.put("gender", "未知");
                item.put("age", 0);
                item.put("city", "");
                item.put("vipLevel", 0);
            }
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", userPage.getTotal());
        result.put("list", items);
        return result;
    }

    /**
     * 查询用户详情
     */
    public Map<String, Object> queryUserDetail(Long userId) {
        User user = this.userMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        Map<String, Object> item = new HashMap<>();
        item.put("id", user.getId());
        item.put("mobile", user.getMobile());
        item.put("status", user.getStatus() != null ? user.getStatus() : "NORMAL");
        item.put("freezeReason", user.getFreezeReason());
        item.put("createdAt", user.getCreated());

        UserInfo userInfo = this.userInfoApi.queryByUserId(userId);
        if (userInfo != null) {
            item.put("nickname", userInfo.getNickName());
            item.put("avatar", userInfo.getLogo());
            item.put("gender", userInfo.getSex() != null ? userInfo.getSex().toString() : "未知");
            item.put("age", userInfo.getAge());
            item.put("city", userInfo.getCity());
            item.put("signature", userInfo.getTags());
        }

        return item;
    }

    /**
     * 封禁用户
     */
    public Boolean freezeUser(Long userId, String reason) {
        User user = new User();
        user.setId(userId);
        user.setStatus("FROZEN");
        user.setFreezeReason(reason);
        return this.userMapper.updateById(user) > 0;
    }

    /**
     * 解封用户
     */
    public Boolean unfreezeUser(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setStatus("NORMAL");
        user.setFreezeReason(null);
        return this.userMapper.updateById(user) > 0;
    }

    /**
     * 重置用户密码（重置为123456）
     */
    public Boolean resetPassword(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setPassword(org.apache.commons.codec.digest.DigestUtils.md5Hex("123456"));
        return this.userMapper.updateById(user) > 0;
    }
}