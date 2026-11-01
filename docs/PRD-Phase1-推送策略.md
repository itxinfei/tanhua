# PRD：推送策略上线（Phase 1 P0）

> FR-ID: **FR-PUSH-01**  
> 优先级：**P0**  
> 负责人：后端 1.人  
> 预估工时：后端 1.5d + 联调测试 0.5d = 2d  
> 依赖项：tracker.ts（用户登录事件上报）  
> FR-ID（需求文档关联）：FR-PUSH-01（推送通知，已有基础框架）

---

## 一、背景与目标

### 1.1 业务背景

PushService（极光 REST API）已就绪，但仅写了基础封装，**无任何业务推送场景**。推送是陌生人社交最重要的召回手段。

**核心问题**：
- 用户配对后不知道（对方需要实时唤起）
- 沉默 3 天用户无任何提醒
- 推送文案单一、无分层策略
- 无免打扰时段（可能扰民）

### 1.2 数据目标

| 指标 | 当前 | 目标 |
|------|:---:|:---:|
| 次日留存 | 25% | 35%+ |
| 配对成功 → 5min 内进入聊天比例 | 15% | 50%+ |
| 沉默用户（3 天）→ 7 日内回访率 | 5% | 15%+ |
| 推送到达率（技术层）| — | ≥ 90% |
| 推送点击率 | — | ≥ 8% |

### 1.3 非目标

- 不在本版本实现富媒体推送（图片/语音推送）
- 不做基于用户画像的个性化推荐推送
- 不引入推送频次控制（仅 MVP 级别基础控制）

---

## 二、推送场景设计

### 2.1 推送清单（一期 6 个场景）

| # | 场景 | 推送时机 | 目标用户 | 文案模板 |
|---|------|---------|---------|---------|
| 1 | **配对成功** | 配对事件实时 | 配对双方用户 | "🎉 你和 XXX 互相喜欢了！Say hi →" |
| 2 | **新消息** | IM 消息到达 | 接收者 | "XXX 给你发了一条新消息" |
| 3 | **今日佳人刷新** | 每日 20:00 | 活跃用户 | "🔮 今日线索更新：一位XX座的小姐姐在等你" |
| 4 | **谁喜欢你提醒** | 每日 10:00 | 有新喜欢但 24h 未查看用户 | "💕 有 3 个人悄悄看了你的主页" |
| 5 | **VIP 到期提醒** | 到期前 7/3/1 天 | VIP 用户 | "VIP 3 天后到期，续费享 8 折" |
| 6 | **沉默用户召回** | 注册后 D3/D7（排除当天注册用户，避免注册当天即被推送）| 3 天未打开用户 | "3 天没见，XX 给你发了消息" |

### 2.2 推送流程

```
业务事件触发
  │
  ├─ 配对成功 → push(match.userId, "配对成功", {type:"match_success", targetId: matchedUserId})
  │
  ├─ 消息到达 → push(msg.receiverId, "新消息", {type:"new_message", senderId: msg.senderId})
  │
  ├─ 定时任务（每日 10:00）→ 扫描有新喜欢但未查看用户 → pushBatch(...)
  │
  ├─ 定时任务（每日 20:00）→ 生成佳人预告 → pushBatch(...)
  │
  ├─ VIP 到期（1天前）→ push(userId, "VIP 快到期", {type:"vip_expiring"})
  │
  └─ 定时任务（每日 12:00）→ 扫描沉默用户 → pushBatch(...)
```

### 2.3 免打扰时段

```
默认免打扰：23:00 - 08:00
用户可在 settings 中调整

实现：pushTime = max(userPushTime, now)
if now in silentWindow:
    schedule push at windowEnd
else:
    send immediately
```

---

## 三、推送接收与点击

### 3.1 客户端处理（已实现 + 增强）

```typescript
// wukongim push 回调或极光 SDK
interface PushPayload {
  type: "match_success" | "new_message" | "daily_best" | "who_likes_me" | "vip_expiring" | "recall";
  title: string;
  body: string;
  targetId?: number;       // 对应 userId 或 postId
  source: "push_service";  // 区分本地通知
  channel: "wukongim" | "jpush";  // IM走WuKongIM长连接，业务推送走极光
}

// App.vue 中监听
uni.onMessage((res) => {
  const payload: PushPayload = res.data?.payload;
  track("push_receive", { push_type: payload.type, campaign_id: payload.campaignId });
  
  // 点击推送
  uni.onClickMessage((res) => {
    track("push_click", { push_type: payload.type, target_id: payload.targetId });
    routerPush(payload);
  });
});
```

### 3.2 推送点击跳转

| 推送类型 | 跳转目标 |
|---------|---------|
| match_success | /pages/chat/chat?userId={targetId} |
| new_message | /pages/chat/chat?userId={targetId} |
| daily_best | /pages/match/index?tab=best |
| who_likes_me | /pages/mine/index?tab=likes |
| vip_expiring | /pages/mine/vip |
| recall | /pages/match/index |

---

## 四、后端实现

### 4.1 新增定时任务

```java
@Configuration
@EnableScheduling
public class PushScheduleTask {

    // 每日 10:00：谁喜欢你提醒
    @Scheduled(cron = "0 0 10 * * ?")
    public void whoLikesMeReminder() {
        // 查询 24h 内有新喜欢但未查看的用户
        List<Long> userIds = pushService.findUnseenLikeUsers(Duration.ofHours(24));
        for (Long uid : userIds) {
            int count = likeService.countUnseenLikes(uid);
            pushService.push(uid, "💕 有 " + count + " 个人悄悄看了你的主页", 
                           PushType.WHO_LIKES_ME, null);
        }
    }
    
    // 每日 20:00：今日佳人预告
    @Scheduled(cron = "0 0 20 * * ?")
    public void dailyBestPreview() {
        // 活跃用户（3 天内有滑动记录）
        List<Long> activeUsers = trackService.findActiveUsers(Duration.ofDays(3));
        for (Long uid : activeUsers) {
            // 计算明日佳人的模糊信息
            DailyBestPreview preview = matchService.getBestPreview(uid);
            pushService.push(uid, "🔮 " + preview.getClueText(), 
                           PushType.DAILY_BEST, null);
        }
    }
    
    // 每日 12:00：沉默用户唤醒（D3/D7/D14，排除当天注册用户）
    @Scheduled(cron = "0 0 12 * * ?")
    public void recallSilentUsers() {
        // WHERE 条件加 register_date < CURDATE() - INTERVAL 3 DAY 排除当天注册
        recallByDay(3, "3 天没见，XX 给你发了消息");
        recallByDay(7, "一周不见，心飞多了很多新朋友");
        recallByDay(14, "想你了，回来看看吧");
    }
}
```

### 4.2 推送记录表

```sql
CREATE TABLE tb_push_record (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    push_type    VARCHAR(32) NOT NULL COMMENT '推送场景',
    title        VARCHAR(255),
    body         TEXT,
    target_id    BIGINT COMMENT '关联对象ID',
    is_sent      TINYINT DEFAULT 0,
    is_clicked   TINYINT DEFAULT 0,
    clicked_at   DATETIME,
    sent_at      DATETIME NOT NULL,
    
    INDEX idx_user_type(user_id, push_type),
    INDEX idx_sent_at(sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送发送记录';
```

### 4.3 推送限流

| 规则 | 说明 |
|------|------|
| 每用户每日最多 | 3 条业务推送（非 IM 消息）|
| 同类推送去重 | 同一类型 24h 内只发 1 次 |
| 静音用户 | 用户关闭推送后，不发送任何业务推送 |
| VIP 到期提醒 | 允许突破每日上限（避免用户权益损失）|

---

## 五、边界场景

| 场景 | 处理 |
|------|------|
| 极光 API 调用失败 | 重试 3 次（1s/2s/4s），失败记录日志 + 报监控 |
| 用户卸载 App 后 token 失效 | 极光返回 invalid_registration → 标记 token 无效 |
| 夜间紧急配对推送 | 免打扰期内不发，推迟到早 8:00 发送（文案加"昨夜"）|
| 用户刚卸载又重装 | token 重新注册，覆盖旧记录 |
| 本地时间与服务器时间偏差 | 以防抖为准（以免打扰窗口为准）|

---

## 六、测试验收

| # | Given | When | Then |
|---|-------|------|------|
| T1 | 用户 A 和用户 B 配对 | 服务端事件触发 | B 收到推送 A |
| T2 | 用户收到新消息 | IM 消息到达 | 锁屏可见推送，点击直达聊天 |
| T3 | 20:00 定时任务 | 活跃用户 | 收到佳人预告推送（注意免打扰时段）|
| T4 | 每日 10:00 | 有新喜欢用户 | 仅 24h 内有新喜欢的人收到，无新喜欢的人不收到 |
| T5 | VIP 明天到期 | 到期前 1 天 | 收到到期提醒 + 续费 8 折 |
| T6 | 沉默 3 天用户 | D3 12:00 | 收到召回推送 |
| T7 | 用户关闭推送（权限）| 拒绝授权后调推送 | 不发送 + 记录"用户已关闭推送" |
| T8 | 推送到达 | 极光回调 | tb_push_record 更新 is_sent=1 |

---

## 七、变更记录

| 版本 | 日期 | 变更内容 |
|:---:|:---:|------|
| v1.0 | 2026-11-01 | 初版。6 个推送场景（配对/消息/佳人/喜欢/VIP/唤醒），每日限流 3 条，免打扰 23-08，3 个定时任务 |
