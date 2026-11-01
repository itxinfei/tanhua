package com.xfjy.sso.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xfjy.sso.mapper.UserMapper;
import com.xfjy.sso.pojo.User;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 注销账号 30 天 PII 匿名化任务（FR-USER-07）
 *
 * <p>口径：到期不物理删行（保留 id 供订单/举报等外键侧对账），改为抹除可识别个人信息
 * ——手机号改写为不可逆占位值、密码重置为随机值，status 保持 DELETED。</p>
 *
 * <p>多实例部署时各节点都会执行；更新条件限定 mobile 仍为原始值，重复执行无副作用。</p>
 */
@Component
public class UserPurgeTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserPurgeTask.class);

    /** 注销后保留期：30 天 */
    private static final int RETENTION_DAYS = 30;

    /** 匿名化手机号前缀，长度与唯一性由 id 保证 */
    private static final String PURGED_MOBILE_PREFIX = "0000000000";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private UserMapper userMapper;

    /**
     * 每日 04:10 扫描：status=DELETED 且 updated 早于 30 天前的账号执行 PII 匿名化
     */
    @Scheduled(cron = "0 10 4 * * ?")
    public void purgeDeletedUsers() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -RETENTION_DAYS);
        Date deadline = calendar.getTime();

        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("status", "DELETED").lt("updated", deadline);
        List<User> users = this.userMapper.selectList(query);
        if (users == null || users.isEmpty()) {
            return;
        }

        int purged = 0;
        for (User user : users) {
            if (user.getMobile() != null && user.getMobile().startsWith(PURGED_MOBILE_PREFIX)) {
                continue; // 已匿名化，跳过
            }
            LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<>();
            update.eq(User::getId, user.getId())
                    .set(User::getMobile, PURGED_MOBILE_PREFIX + user.getId())
                    .set(User::getPassword, DigestUtils.md5Hex(String.valueOf(RANDOM.nextLong())))
                    .set(User::getFreezeReason, null)
                    .set(User::getUpdated, new Date());
            if (this.userMapper.update(null, update) > 0) {
                purged++;
            }
        }
        LOGGER.info("[注销清理] 到期账号 PII 匿名化完成 scanned={} purged={}", users.size(), purged);
    }
}
