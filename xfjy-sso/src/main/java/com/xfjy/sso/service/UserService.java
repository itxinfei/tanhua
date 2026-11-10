package com.xfjy.sso.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xfjy.sso.mapper.UserMapper;
import com.xfjy.sso.pojo.User;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
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
     * 测试账号固定验证码：仅 user 表 ID=1 的手机号可用此码，其他手机号必须使用真实短信验证码。
     */
    private static final String TEST_USER_FIXED_CODE = "123456";
    /** 测试账号用户ID（用户指定，该手机号专用于联调测试） */
    private static final Long TEST_USER_ID = 1L;
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
        if (TEST_USER_FIXED_CODE.equals(code)) {
            // 仅测试账号（ID=1）允许固定验证码；其他手机号必须走真实短信验证码
            QueryWrapper<User> qw = new QueryWrapper<>();
            qw.eq("mobile", phone).select("id");
            User existing = this.userMapper.selectOne(qw);
            if (existing != null && TEST_USER_ID.equals(existing.getId())) {
                LOGGER.warn("测试账号使用固定验证码登录，phone={}", phone);
                redisTemplate.delete(key);
                redisTemplate.delete(failKey);
                return true;
            }
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

        // 取用户，不存在则注册。
        // 并发首次登录不靠"先查后插"保证唯一：tb_user.mobile 上有唯一索引 uk_user_mobile，
        // 同一手机号只有一个 insert 能成功，失败方捕获 DuplicateKeyException 后按手机号回查，
        // 从而只会产生一个账号，也不会因为 selectOne 命中多行抛 TooManyResultsException。
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("mobile", phone);
        User user = this.userMapper.selectOne(queryWrapper);

        boolean isNew = (user == null);
        if (isNew) {
            User created = new User();
            created.setMobile(phone);
            // 验证码登录不设置可用密码，写入随机值锁死密码登录
            created.setPassword(DigestUtils.md5Hex(String.valueOf(RANDOM.nextLong())));
            created.setStatus("NORMAL");
            created.setCreated(new java.util.Date());
            created.setUpdated(new java.util.Date());
            try {
                this.userMapper.insert(created);
                user = created;
            } catch (DuplicateKeyException e) {
                // 另一并发请求已插入同一手机号：回查已存在用户，按老用户继续登录
                QueryWrapper<User> requery = new QueryWrapper<>();
                requery.eq("mobile", phone);
                user = this.userMapper.selectOne(requery);
                if (user == null) {
                    throw e;
                }
                isNew = false;
            }
        }

        // 已封禁(FROZEN)：返回 403 + 封禁原因（FR-AUTH-01 验收口径）；已注销(DELETED)：按验证码错误同等响应，不暴露账号状态
        if (!"NORMAL".equals(user.getStatus())) {
            if ("FROZEN".equals(user.getStatus())) {
                Map<String, Object> frozen = new HashMap<>();
                frozen.put("error", "FROZEN");
                frozen.put("reason", StringUtils.defaultIfBlank(user.getFreezeReason(), "请联系客服"));
                return frozen;
            }
            return null;
        }

        // Sa-Token 签发 token（会话存 Redis，所有服务共享校验）
        // loginId 加 "user-" 前缀，与后台 AdminTokenInterceptor 的 "admin-" 命名空间隔离，防止跨端会话串号
        // 注意：Sa-Token 的 loginId 不允许包含冒号（会抛 SaTokenException），故用连字符
        StpUtil.login("user-" + user.getId());

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
        // 命名空间隔离：只接受 "user-" 前缀的 loginId，后台 admin token 在此直接拒绝
        if (loginId == null || !loginId.toString().startsWith("user-")) {
            return null;
        }
        User user = this.userMapper.selectById(Long.valueOf(loginId.toString().substring("user-".length())));
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

        // 原子更新：WHERE 中用 NOT EXISTS 再次排除并发占用，
        // 防止两个用户同时换绑同一手机号时 check-then-act 竞态导致双占
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", userId)
                .eq("status", "NORMAL")
                .apply("NOT EXISTS (SELECT 1 FROM tb_user u2 WHERE u2.mobile = {0} AND u2.id != {1})", newPhone, userId)
                .set("mobile", newPhone)
                .set("updated", new java.util.Date());
        int updated;
        try {
            updated = this.userMapper.update(null, updateWrapper);
        } catch (DuplicateKeyException e) {
            // 唯一索引 uk_user_mobile 兜底：并发换绑同一手机号时，NOT EXISTS 也可能同时通过，
            // 后到的那条会撞唯一键，按"手机号已被占用"返回，而不是 500
            return 2;
        }
        if (updated == 0) {
            // 并发场景下手机号被他人抢先占用，或用户状态已变更
            return 2;
        }
        return 0;
    }

    /**
     * 封禁用户（内部接口，由 xfjy-server 后台经内部 token 调用）。
     *
     * @param userId 用户 id
     * @param reason 封禁原因
     * @return 是否成功
     */
    public Boolean freezeUser(Long userId, String reason) {
        User user = new User();
        user.setId(userId);
        user.setStatus("FROZEN");
        user.setFreezeReason(reason);
        return this.userMapper.updateById(user) > 0;
    }

    /**
     * 解封用户（内部接口，由 xfjy-server 后台经内部 token 调用）。
     *
     * @param userId 用户 id
     * @return 是否成功
     */
    public Boolean unfreezeUser(Long userId) {
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId)
                .set("status", "NORMAL")
                .set("freeze_reason", null);
        return this.userMapper.update(null, wrapper) > 0;
    }

    /**
     * 重置用户密码（内部接口，由 xfjy-server 后台经内部 token 调用）。
     * 密码以 md5Hex 存储，与注册/登录校验口径一致。
     *
     * @param userId      用户 id
     * @param newPassword 明文新密码（由调用方传入，默认 123456）
     * @return 是否成功
     */
    public Boolean resetPassword(Long userId, String newPassword) {
        User user = new User();
        user.setId(userId);
        user.setPassword(DigestUtils.md5Hex(newPassword));
        return this.userMapper.updateById(user) > 0;
    }

    /**
     * 注销用户（软删除，status=DELETED，内部接口，由 xfjy-server 后台经内部 token 调用）。
     *
     * @param userId 用户 id
     * @return 是否成功
     */
    public Boolean deactivateUser(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setStatus("DELETED");
        return this.userMapper.updateById(user) > 0;
    }
}
