package com.xfjy.service.api;

import com.xfjy.service.api.SwipeLogApi;
import com.xfjy.user.mapper.SwipeLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

/**
 * 滑动日志服务实现（xfjy-user-service 侧；由 BFF 层通过 @HttpExchange 代理调用）
 */
@Slf4j
@RestController
public class SwipeLogApiImpl implements SwipeLogApi {

    @Autowired
    private SwipeLogMapper swipeLogMapper;

    @Override
    public Boolean recordSwipe(Long userId, Long targetId, String action, Integer matched) {
        try {
            int rows = this.swipeLogMapper.recordSwipe(userId, targetId, action, matched != null ? matched : 0);
            // rows=0 时（幂等跳过）仍视为成功，不阻断主链路
            return true;
        } catch (Exception e) {
            // 滑动日志写入失败不得阻断主链路（旁路：仅打日志）
            log.warn("滑动日志写入失败：userId={}, targetId={}, action={}", userId, targetId, action, e);
            return false;
        }
    }

    @Override
    public Long countTodaySwipes(Long userId) {
        try {
            Long count = this.swipeLogMapper.countTodaySwipes(userId);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("查询今日滑动次数失败：userId={}", userId, e);
            // 数据库异常时返回 0（让请求继续走主链路 + 权益兜底拦截）
            return 0L;
        }
    }
}
