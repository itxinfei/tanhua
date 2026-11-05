package com.xfjy.service.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * SSO 用户管理内部契约（@HttpExchange 由 xfjy-server 编排层代理调用）。
 *
 * <p>所有端点位于 sso 的 {@code /internal/user/**}（非 /user 前缀，网关不路由，仅 xfjy-server 经内部网络可达），
 * 调用；sso 侧由 xfjy-api 的 {@code InternalTokenAutoConfiguration} 在 {@code /**} 注册校验拦截器。</p>
 */
@HttpExchange("/internal/user")
public interface UserApi {

    /**
     * 封禁用户（status=FROZEN，记录封禁原因）。
     */
    @PostExchange("/freeze")
    @PostMapping("/freeze")
    Boolean freeze(@RequestParam("userId") Long userId, @RequestParam("reason") String reason);

    /**
     * 解封用户（status=NORMAL，清空封禁原因）。
     */
    @PostExchange("/unfreeze")
    @PostMapping("/unfreeze")
    Boolean unfreeze(@RequestParam("userId") Long userId);

    /**
     * 重置用户密码（明文 newPassword 由调用方传入，sso 侧以 md5Hex 存储，与登录校验口径一致）。
     */
    @PostExchange("/reset-password")
    @PostMapping("/reset-password")
    Boolean resetPassword(@RequestParam("userId") Long userId,
                         @RequestParam("newPassword") String newPassword);

    /**
     * 注销用户（软删除，status=DELETED）。
     */
    @PostExchange("/deactivate")
    @PostMapping("/deactivate")
    Boolean deactivate(@RequestParam("userId") Long userId);
}
