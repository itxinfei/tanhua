package com.xfjy.server.service;

import com.xfjy.service.api.HuanXinApi;
import com.xfjy.service.api.WuKongImApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * IM 通道策略委派器
 *
 * implements {@link HuanXinApi}，按 tb_sys_config.im.provider 配置运行时委派：
 * - wukongim（默认）：委派到 {@link WuKongImApi}（开源 WuKongIM）
 * - huanxin：委派到原 {@link HuanXinApi}（环信第三方）
 *
 * 5个调用点（HuanXinController/AdminUserService/KefuService/IMService/TodayBestService）
 * 注入类型仍为 HuanXinApi，由容器注入本委派器，实现零改动通道切换。
 *
 * 配置读取带 30s Redis 缓存，后台改配置约 30 秒内生效，无需重启。
 */
@Slf4j
public class ImProviderDelegate implements HuanXinApi {

    private final HuanXinApi huanXinApiClient;
    private final WuKongImApi wuKongImApiClient;
    private final SysConfigService sysConfigService;

    public ImProviderDelegate(HuanXinApi huanXinApiClient,
                              WuKongImApi wuKongImApiClient,
                              SysConfigService sysConfigService) {
        this.huanXinApiClient = huanXinApiClient;
        this.wuKongImApiClient = wuKongImApiClient;
        this.sysConfigService = sysConfigService;
    }

    /** 当前是否走 WuKongIM 通道 */
    private boolean useWuKongIm() {
        return sysConfigService.isWuKongIm();
    }

    @Override
    public void registerContact(Long userId, Long friendId) {
        if (useWuKongIm()) {
            wuKongImApiClient.registerContact(userId, friendId);
        } else {
            huanXinApiClient.registerContact(userId, friendId);
        }
    }

    @Override
    public void sendMessage(String target, String msg) {
        if (useWuKongIm()) {
            wuKongImApiClient.sendMessage(target, msg);
        } else {
            huanXinApiClient.sendMessage(target, msg);
        }
    }

    @Override
    public boolean registerUser(String username, String password) {
        if (useWuKongIm()) {
            return wuKongImApiClient.registerUser(username, password);
        }
        return huanXinApiClient.registerUser(username, password);
    }

    @Override
    public String getUserToken(String username, String password) {
        if (useWuKongIm()) {
            return wuKongImApiClient.getUserToken(username, password);
        }
        return huanXinApiClient.getUserToken(username, password);
    }

    @Override
    public List<Map<String, Object>> getChatSessions(String userId, Integer limit) {
        if (useWuKongIm()) {
            return wuKongImApiClient.getChatSessions(userId, limit);
        }
        return huanXinApiClient.getChatSessions(userId, limit);
    }

    @Override
    public Map<String, Object> getChatMessages(String fromUserId, String toUserId,
                                                Integer limit, String cursor) {
        if (useWuKongIm()) {
            return wuKongImApiClient.getChatMessages(fromUserId, toUserId, limit, cursor);
        }
        return huanXinApiClient.getChatMessages(fromUserId, toUserId, limit, cursor);
    }

    @Override
    public Long getTodayMessageCount() {
        if (useWuKongIm()) {
            return wuKongImApiClient.getTodayMessageCount();
        }
        return huanXinApiClient.getTodayMessageCount();
    }
}