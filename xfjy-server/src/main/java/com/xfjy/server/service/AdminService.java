package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.dev33.satoken.stp.StpUtil;
import com.xfjy.server.mapper.AdminMapper;
import com.xfjy.server.pojo.Admin;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理员服务：登录鉴权（Sa-Token）
 */
@Service
public class AdminService {

    @Autowired
    private AdminMapper adminMapper;

    /**
     * 管理员登录
     *
     * @param username 账号
     * @param password 密码（明文）
     * @return {token, admin}；登录失败返回null
     */
    public Map<String, Object> login(String username, String password) {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return null;
        }

        // 查询管理员
        QueryWrapper<Admin> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        Admin admin = this.adminMapper.selectOne(queryWrapper);

        if (admin == null) {
            return null;
        }

        // 校验状态
        if ("DISABLED".equals(admin.getStatus())) {
            return null;
        }

        // 校验密码（MD5）
        String md5Password = org.apache.commons.codec.digest.DigestUtils.md5Hex(password);
        if (!md5Password.equals(admin.getPassword())) {
            return null;
        }

        // Sa-Token 登录，签发Token（有效期由 sa-token 配置管理）
        StpUtil.login(admin.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", StpUtil.getTokenValue());
        result.put("admin", admin);
        return result;
    }

    /**
     * 退出登录（使Token失效）
     */
    public void logout(String token) {
        if (StringUtils.isNotBlank(token)) {
            StpUtil.logoutByTokenValue(token);
        }
    }

    /**
     * 根据ID查询管理员
     */
    public Admin queryAdminById(Long id) {
        return this.adminMapper.selectById(id);
    }

    /**
     * 更新管理员信息
     */
    public void updateAdmin(Admin admin) {
        this.adminMapper.updateById(admin);
    }
}