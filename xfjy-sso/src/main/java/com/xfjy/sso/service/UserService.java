package com.xfjy.sso.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xfjy.sso.mapper.UserMapper;
import com.xfjy.sso.pojo.User;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final String CODE_FAIL_KEY_PREFIX = "XFJY_SSO_CODE_FAIL_";
    private static final Duration CODE_EXPIRE = Duration.ofMinutes(5);
    /** 验证码连续错误次数上限，达到即作废该码（NFR-SEC-10） */
    private static final int CODE_FAIL_LIMIT = 5;
    /**
     * 开发用万能验证码（未接入短信服务商时本地联调使用）。
     *
     * <p><b>安全债（用户拍板保留，11.7-15）</b>：命中此码可登录任意手机号，
     * 上线前必须移除或改为环境开关；每次命中打 WARN 日志便于排查。</p>
     */
    private static final String DEV_MASTER_CODE = "123456";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    /**
     * 校验验证码：必须与 Redis 中的码一致（或命中开发万能码）。
     *
     * <p>失败计数：同一手机号连续错 {@value #CODE_FAIL_LIMIT} 次立即作废该验证码，
     * 必须重新发送（NFR-SEC-10，封掉 5 分钟窗口内的穷举）。</p>
     *
     * @return true=校验通过（并清除该码与失败计数）
     */
    private boolean verifyCode(String phone, String code) {
        if (StringUtils.isBlank(phone) || StringUtils.isBlank(code)) {
            return false;
        }
        String key = CODE_KEY_PREFIX + phone;
        String failKey = CODE_FAIL_KEY_PREFIX + phone;
        if (DEV_MASTER_CODE.equals(code)) {
            LOGGER.warn("使用开发万能验证码登录，phone={}（该码上线前必须移除）", phone);
            redisTemplate.delete(key);
            redisTemplate.delete(failKey);
            return true;
        }
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            return false;
        }
        if (code.equals(cached)) {
            redisTemplate.delete(key);
            redisTemplate.delete(failKey);
            return true;
        }
        // 校验失败：累加错误次数，达上限作废该码
        Long fails = redisTemplate.opsForValue().increment(failKey);
        if (fails != null && fails == 1L) {
            redisTemplate.expire(failKey, CODE_EXPIRE);
        }
        if (fails != null && fails >= CODE_FAIL_LIMIT) {
            redisTemplate.delete(key);
            redisTemplate.delete(failKey);
            LOGGER.warn("验证码连续错误达上限已作废，phone={} fails={}", phone, fails);
        }
        return false;
    }

    /**
     * 发送短信验证码
     *
     * @return 6位验证码（本地开发模式返回给调用方打印，生产环境应由短信通道下发）
     */
    public String sendCode(String phone) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + phone, code, CODE_EXPIRE);
        redisTemplate.delete(CODE_FAIL_KEY_PREFIX + phone);
        // TODO: 接入阿里云短信服务后，此处改为调用短信通道下发，不再返回验证码明文
        return code;
    }

    /**
     * 手机号 + 验证码登录/注册
     *
     * <p>返回 null=验证码错误；返回含 error 键的 Map=账号态异常（FROZEN 携带封禁原因）；正常返回 {token,user,isNew}</p>
     */
    public Map<String, Object> loginVerification(String phone, String code) {
        if (StringUtils.isBlank(phone) || StringUtils.isBlank(code)) {
            return null;
        }

        if (!verifyCode(phone, code)) {
            return null;
        }

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
            // 已封禁(FROZEN)：返回 403 + 封禁原因（FR-AUTH-01 验收口径）；已注销(DELETED)：按验证码错误同等响应，不暴露账号状态
            if ("FROZEN".equals(user.getStatus())) {
                Map<String, Object> frozen = new HashMap<>();
                frozen.put("error", "FROZEN");
                frozen.put("reason", StringUtils.defaultIfBlank(user.getFreezeReason(), "请联系客服"));
                return frozen;
            }
            return null;
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

        if (!verifyCode(newPhone, code)) {
            return 1;
        }

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
