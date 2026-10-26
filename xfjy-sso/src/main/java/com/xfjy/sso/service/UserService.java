package com.xfjy.sso.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.sso.mapper.UserMapper;
import com.xfjy.sso.pojo.User;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SSO 用户服务：验证码登录/注册、Token 校验
 */
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String CODE_KEY_PREFIX = "XFJY_SSO_CODE_";
    private static final Duration CODE_EXPIRE = Duration.ofMinutes(5);
    /**
     * 开发用万能验证码（未接入短信服务商时本地联调使用）
     */
    private static final String DEV_MASTER_CODE = "123456";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 发送短信验证码
     *
     * @return 6位验证码（本地开发模式返回给调用方打印，生产环境应由短信通道下发）
     */
    public String sendCode(String phone) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + phone, code, CODE_EXPIRE);
        // TODO: 接入阿里云短信服务后，此处改为调用短信通道下发，不再返回验证码明文
        return code;
    }

    /**
     * 手机号 + 验证码登录/注册
     *
     * @return {token, user, isNew}；验证码错误返回 null
     */
    public Map<String, Object> loginVerification(String phone, String code) {
        if (StringUtils.isBlank(phone) || StringUtils.isBlank(code)) {
            return null;
        }

        // 校验验证码：Redis 中的验证码 或 开发万能验证码
        String cached = redisTemplate.opsForValue().get(CODE_KEY_PREFIX + phone);
        boolean codeValid = code.equals(cached) || DEV_MASTER_CODE.equals(code);
        if (!codeValid) {
            return null;
        }
        redisTemplate.delete(CODE_KEY_PREFIX + phone);

        // 查询用户，不存在则注册
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("mobile", phone);
        User user = this.userMapper.selectOne(queryWrapper);

        boolean isNew = (user == null);
        if (isNew) {
            user = new User();
            user.setMobile(phone);
            // 验证码登录不设置可用密码，写入随机值锁死密码登录
            user.setPassword(DigestUtils.md5Hex(String.valueOf(RANDOM.nextLong())));
            user.setStatus("NORMAL");
            user.setCreated(new java.util.Date());
            user.setUpdated(new java.util.Date());
            this.userMapper.insert(user);
        } else if (!"NORMAL".equals(user.getStatus())) {
            return null; //已封禁(FROZEN)或已注销(DELETED)
        }

        // Sa-Token 签发 token（会话存 Redis，所有服务共享校验）
        StpUtil.login(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", StpUtil.getTokenValue());
        result.put("user", user);
        result.put("isNew", isNew);
        return result;
    }

    /**
     * 根据 token 查询用户（xfjy-server 的 TokenInterceptor 调用此契约）
     *
     * @return 用户对象；token 无效返回 null
     */
    public User queryUserByToken(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            return null;
        }
        User user = this.userMapper.selectById(Long.valueOf(loginId.toString()));
        //已注销(DELETED)或已封禁(FROZEN)的用户，token 立即失效
        if (user == null || !"NORMAL".equals(user.getStatus())) {
            return null;
        }
        return user;
    }

    /**
     * 换绑手机号（需验证新手机号的短信验证码）
     *
     * @param userId   当前登录用户ID
     * @param newPhone 新手机号
     * @param code     新手机号收到的验证码
     * @return 0-成功，1-验证码错误或用户无效，2-手机号已被占用
     */
    public int changePhone(Long userId, String newPhone, String code) {
        if (userId == null || StringUtils.isBlank(newPhone) || StringUtils.isBlank(code)) {
            return 1;
        }

        // 校验验证码：Redis 中的验证码 或 开发万能验证码
        String cached = redisTemplate.opsForValue().get(CODE_KEY_PREFIX + newPhone);
        boolean codeValid = code.equals(cached) || DEV_MASTER_CODE.equals(code);
        if (!codeValid) {
            return 1;
        }
        redisTemplate.delete(CODE_KEY_PREFIX + newPhone);

        // 校验新手机号未被他人占用
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("mobile", newPhone);
        User existing = this.userMapper.selectOne(queryWrapper);
        if (existing != null && !existing.getId().equals(userId)) {
            return 2;
        }

        // 更新当前用户手机号
        User user = this.userMapper.selectById(userId);
        if (user == null || !"NORMAL".equals(user.getStatus())) {
            return 1;
        }
        user.setMobile(newPhone);
        user.setUpdated(new java.util.Date());
        this.userMapper.updateById(user);
        return 0;
    }
}
