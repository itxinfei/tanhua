package com.xfjy.service.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 滑动日志服务契约（由 domain-service 实现，BFF 层通过此接口代理）
 *
 * <p>职责：记录每次滑动动作 + 查询当日已用次数。</p>
 * <p>后端次数校验防前端绕过：前端 storage 造假 → 后端以 tb_swipe_log 实际写入次数为准。</p>
 */
@HttpExchange("/api/swipe-log")
public interface SwipeLogApi {

    /**
     * 记录一次滑动动作
     *
     * @param userId   发起滑动用户 ID
     * @param targetId 被滑动用户 ID
     * @param action   动作：like | superlike | dislike
     * @param matched  是否互喜（0/1）
     * @return true=记录成功
     */
    @PostExchange("/record")
    @PostMapping("/record")
    Boolean recordSwipe(@RequestParam("userId") Long userId,
                        @RequestParam("targetId") Long targetId,
                        @RequestParam("action") String action,
                        @RequestParam(value = "matched", defaultValue = "0") Integer matched);

    /**
     * 查询用户今日已滑动次数（按 action 维度汇总，三种动作都计入总次数）
     *
     * @param userId 用户 ID
     * @return 今日已滑动次数（dislike 不计数；like+superlike 均计数）
     */
    @GetExchange("/count-today")
    @GetMapping("/count-today")
    Long countTodaySwipes(@RequestParam("userId") Long userId);
}
