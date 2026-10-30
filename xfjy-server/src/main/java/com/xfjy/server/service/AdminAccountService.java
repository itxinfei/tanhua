package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.server.mapper.AdminMapper;
import com.xfjy.server.pojo.Admin;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 后台管理员账号管理服务（仅SUPER_ADMIN可操作）
 */
@Service
public class AdminAccountService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAccountService.class);

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_OPERATOR = "OPERATOR";

    @Autowired
    private AdminMapper adminMapper;

    /**
     * 分页查询管理员列表
     */
    public Map<String, Object> queryAdminPage(Integer page, Integer pageSize, String keyword) {
        QueryWrapper<Admin> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like("username", keyword).or().like("nickname", keyword);
        }
        wrapper.orderByDesc("created");

        IPage<Admin> adminPage = this.adminMapper.selectPage(new Page<>(page, pageSize), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", adminPage.getTotal());
        result.put("list", adminPage.getRecords());
        return result;
    }

    /**
     * 新增管理员
     */
    public Boolean createAdmin(String username, String password, String nickname, String role) {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return false;
        }
        if (!isValidRole(role)) {
            return false;
        }

        // 检查用户名唯一
        QueryWrapper<Admin> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        Long count = this.adminMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            return false;
        }

        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setPassword(DigestUtils.md5Hex(password));
        admin.setNickname(StringUtils.isNotBlank(nickname) ? nickname : username);
        admin.setRole(role);
        admin.setStatus("NORMAL");
        admin.setCreated(new Date());
        admin.setUpdated(new Date());
        return this.adminMapper.insert(admin) > 0;
    }

    /**
     * 编辑管理员（昵称/角色/头像）
     */
    public Boolean updateAdmin(Long id, String nickname, String role, String avatar) {
        Admin admin = this.adminMapper.selectById(id);
        if (admin == null) {
            return false;
        }
        if (StringUtils.isNotBlank(role)) {
            if (!isValidRole(role)) {
                return false;
            }
            // 不允许自我降级
            admin.setRole(role);
        }
        if (StringUtils.isNotBlank(nickname)) {
            admin.setNickname(nickname);
        }
        admin.setAvatar(avatar);
        admin.setUpdated(new Date());
        this.adminMapper.updateById(admin);
        return true;
    }

    /**
     * 禁用/启用管理员
     */
    public Boolean toggleStatus(Long id, String status) {
        if (!"NORMAL".equals(status) && !"DISABLED".equals(status)) {
            return false;
        }
        Admin admin = this.adminMapper.selectById(id);
        if (admin == null) {
            return false;
        }
        // 不允许禁用超级管理员
        if (ROLE_SUPER_ADMIN.equals(admin.getRole()) && "DISABLED".equals(status)) {
            return false;
        }
        admin.setStatus(status);
        admin.setUpdated(new Date());
        return this.adminMapper.updateById(admin) > 0;
    }

    /**
     * 重置管理员密码
     */
    public Boolean resetPassword(Long id, String newPassword) {
        if (StringUtils.isBlank(newPassword) || newPassword.length() < 6) {
            return false;
        }
        Admin admin = this.adminMapper.selectById(id);
        if (admin == null) {
            return false;
        }
        admin.setPassword(DigestUtils.md5Hex(newPassword));
        admin.setUpdated(new Date());
        return this.adminMapper.updateById(admin) > 0;
    }

    private boolean isValidRole(String role) {
        return ROLE_SUPER_ADMIN.equals(role) || ROLE_ADMIN.equals(role) || ROLE_OPERATOR.equals(role);
    }
}