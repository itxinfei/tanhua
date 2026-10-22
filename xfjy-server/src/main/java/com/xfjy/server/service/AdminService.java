package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.server.mapper.AdminMapper;
import com.xfjy.server.pojo.Admin;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 管理员服务：登录鉴权 + Token管理
 */
@Service
public class AdminService {

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String REDIS_KEY_PREFIX = "XFJY_ADMIN_TOKEN_";
    private static final Duration TOKEN_EXPIRE = Duration.ofHours(24); //Token有效期24小时

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

        // 生成Token并存入Redis
        String token = UUID.randomUUID().toString().replace("-", "");
        String redisKey = REDIS_KEY_PREFIX + token;
        // 存储管理员ID，查询时再从数据库获取完整信息
        this.redisTemplate.opsForValue().set(redisKey, String.valueOf(admin.getId()), TOKEN_EXPIRE);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("admin", admin);
        return result;
    }

    /**
     * 根据Token查询管理员（供拦截器使用）
     *
     * @param token 管理员Token
     * @return Admin对象，未找到返回null
     */
    public Admin queryAdminByToken(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }

        String redisKey = REDIS_KEY_PREFIX + token;
        String adminIdStr = this.redisTemplate.opsForValue().get(redisKey);
        if (StringUtils.isBlank(adminIdStr)) {
            return null;
        }

        try {
            Long adminId = Long.valueOf(adminIdStr);
            Admin admin = this.adminMapper.selectById(adminId);
            if (admin != null && !"DISABLED".equals(admin.getStatus())) {
                // 刷新Token有效期
                this.redisTemplate.expire(redisKey, TOKEN_EXPIRE);
                return admin;
            }
        } catch (NumberFormatException e) {
            // Token无效，清除
            this.redisTemplate.delete(redisKey);
        }
        return null;
    }

    /**
     * 退出登录（删除Token）
     */
    public void logout(String token) {
        if (StringUtils.isNotBlank(token)) {
            this.redisTemplate.delete(REDIS_KEY_PREFIX + token);
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