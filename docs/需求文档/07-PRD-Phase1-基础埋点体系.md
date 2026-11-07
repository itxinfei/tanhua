# PRD：基础埋点体系（Phase 1 P0）

> FR-ID: **FR-ENGINE**（基础埋点体系，归入 FR-ENGINE 系列——埋点数据驱动推荐引擎）
> 优先级：**P0**  
> 负责人：前端 1 人  
> 预估工时：前端 1.5d + 联调测试 0.5d = 2d  
> 依赖项：无  
> FR-ID（需求文档关联）：所有功能 PRD 依赖本埋点体系进行效果验证

---

## 一、背景与目标

### 1.1 业务背景

当前项目**完全没有埋点系统**：
- 不知道用户在哪里流失
- 无法验证哪个功能改进有效
- 无法计算核心指标（配对率、首消息率等）

**核心诉求**：建立最小可用（MVP）埋点体系，覆盖 3 大核心漏斗，支撑 Phase 1-3 所有功能的效果验证。

### 1.2 数据目标

| 指标 | 当前 | 目标 |
|------|:---:|:---:|
| 核心漏斗可量化比例 | 0% | 100% |
| A/B 测试能力 | 无 | 有（后续迭代）|
| 用户行为日志完整度 | 0% | ≥ 95% |

### 1.3 非目标

- 不在本版本接第三方 SDK（神策/GIO/友盟）
- 不做实时报表（MVP 仅本地持久化 + 定时批量上报）
- 不做复杂用户画像（仅采集基础行为）

---

## 二、设计原则

### 2.1 三层埋点架构

```
┌─────────────────────────────────────────┐
│  应用层（前端页面 + 交互组件）              │
│  调用：track("match_success", {userId})   │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  采集层（src/utils/tracker.ts）          │
│  事件队列 + 去重 + 本地持久化 + 批量上报     │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│  服务端接收（POST /events/batch）          │
│  入库 + 定期导出至 MySQL/Hive             │
└─────────────────────────────────────────┘
```

### 2.2 事件命名规范

```
{业务域}_{动作}_{对象}

示例：
  match_swipe_right        → 匹配页右滑（喜欢）
  match_swipe_left         → 匹配页左滑（跳过）
  match_popup_icebreaker   → 配对弹窗点击破冰
  vip_page_view            → VIP 页曝光
  push_click               → 推送点击
```

---

## 三、核心埋点清单

### 3.1 激活漏斗（Registration Funnel）

| 事件名 | 触发时机 | 关键参数 |
|--------|---------|---------|
| `app_launch` | App cold start | launch_type: cold/warm/hot, source: icon/push/share |
| `register_page_view` | 注册/登录页曝光 | source |
| `send_sms_click` | 点击发送验证码 | phone_prefix |
| `register_success` | 注册/登录完成 | method: phone/wechat, user_id |
| `interest_page_view` | 兴趣选择页曝光 | source: first_login/settings |
| `interest_save` | 兴趣保存成功 | interest_count, time_spent_sec |
| `main_tab_view` | 匹配页首次曝光 | user_id, is_new_user |

### 3.2 匹配漏斗（Match Funnel）

| 事件名 | 触发时机 | 关键参数 |
|--------|---------|---------|
| `match_card_view` | 卡片展示 | card_index, card_user_id |
| `match_swipe_right` | 右滑（喜欢）| card_user_id, card_index, is_super_like |
| `match_swipe_left` | 左滑（跳过）| card_user_id, card_index |
| `match_swipe_out` | 滑动次数耗尽 | used_count, is_vip |
| `match_success` | 配对成功 | matched_user_id, time_to_match_days |
| `match_unlock_best` | 今日佳人解锁 | unlock_threshold, swipe_count |
| `match_icebreaker_view` | 破冰弹窗展示 | matched_user_id |
| `match_icebreaker_send` | 点击发送破冰 | icebreaker_id, send_type: direct/edit/skip |

### 3.3 转化漏斗（Monetization Funnel）

| 事件名 | 触发时机 | 关键参数 |
|--------|---------|---------|
| `vip_page_view` | VIP 套餐页曝光 | source: swipe_out/nav/push |
| `vip_package_click` | 点击具体套餐 | package_id, price |
| `vip_purchase_click` | 点击购买按钮 | package_id, price |
| `vip_purchase_success` | 支付成功回调 | package_id, price, order_id |
| `vip_purchase_fail` | 支付失败 | package_id, error_code |
| `ads_slot_view` | 广告引导位曝光 | slot_id |
| `ads_impression` | 广告展示完成 | slot_id, ad_provider |

### 3.4 推送漏斗（Push Funnel）

| 事件名 | 触发时机 | 关键参数 |
|--------|---------|---------|
| `push_register` | 推送 token 注册成功 | platform: ios/android/mp |
| `push_receive` | 收到推送 | push_type, campaign_id |
| `push_click` | 点击推送 | push_type, campaign_id, page_target |
| `push_dismiss` | 划掉推送 | push_type |

### 3.5 内容消费漏斗（Content Funnel）

| 事件名 | 触发时机 | 关键参数 |
|--------|---------|---------|
| `feed_view` | Feed 条曝光 | post_id, author_id |
| `feed_detail_click` | 点击动态详情 | post_id, author_id |
| `feed_like` | 点赞动态 | post_id, author_id |
| `feed_comment_click` | 点击评论框 | post_id |
| `feed_comment_send` | 发送评论 | post_id, comment_length |
| `video_view` | 视频曝光 | video_id, author_id |
| `video_like` | 视频点赞 | video_id |
| `video_follow` | 关注视频作者 | author_id |

---

## 四、技术实现

### 4.1 前端 SDK（src/utils/tracker.ts）

```typescript
// 核心 API
track(event: string, params?: Record<string, any>): void
identify(userId: string): void
clear(): void

// 内部实现
class Tracker {
  private queue: Event[] = []
  private flushTimer: number | null = null
  
  track(event, params) {
    const evt = {
      event,
      params: { ...params, ts: Date.now(), uid: this.userId },
      session_id: this.sessionId
    }
    this.queue.push(evt)
    this.persistLocal()
    if (this.queue.length >= 10) this.flush()  // 满10条立即上报
  }
  
  flush() {
    if (this.queue.length === 0) return
    const batch = this.queue.splice(0, 50)
    uni.request({
      url: `${BASE_URL}/events/batch`,
      method: 'POST',
      data: { events: batch, deviceInfo: this.getDevice() },
      complete: () => this.persistLocal(),
    })
  }
}
```

### 4.2 用户与设备标识

| 标识 | 来源 | 生命周期 |
|------|------|--------|
| user_id | token 解析后获得 | 登录后有效 7 天 |
| device_id | `uni.getSystemInfoSync().deviceId` | 永久，设备唯一 |
| session_id | 每次 App 启动生成 UUID | App 进程存活期间 |

### 4.3 本地持久化

- 使用 `uni.setStorageSync('xfjy_event_queue', JSON.stringify(queue))`
- App 启动时恢复未上报事件
- 单次 flush 最多 50 条，失败 3 次后丢弃（防存储膨胀）

### 4.4 服务端接收

```java
// xfjy-server 新增
@RestController
@RequestMapping("/events")
public class EventController {
    @PostMapping("/batch")
    public Result<Void> batch(@RequestBody EventBatch batch) {
        log.info("track: {}", batch);
        // MVP：仅写日志文件，后续接 Kafka → ClickHouse
        return Result.ok();
    }
}
```

---

## 五、接口错误响应

> 本 PRD 唯一的服务端接口是 `POST /events/batch`，错误响应规范如下：

| 接口 | HTTP | 错误码 | 场景 | message |
|------|:---:|:---:|------|----------|
| `POST /events/batch` | 400 | `INVALID_BATCH` | events 为空或超过 50 条 | "事件批次不合法" |
| `POST /events/batch` | 401 | `AUTH_FAILED` | Token 解析失败（如传了 token 但过期）| "请先登录" |
| `POST /events/batch` | 413 | `BATCH_TOO_LARGE` | 单次请求 body > 1MB | "请求体过大" |
| `POST /events/batch` | 503 | `QUEUE_FULL` | 服务端消费队列满 | "服务暂不可用" |

> 埋点接口错误不阻塞主流程：前端 catch 后仅保本地，不展示 toast，不重定向。

### 5.1 依赖项与调用链

- **前置依赖**：无（tracker 是基础组件）
- **上游调用方**：所有业务 PRD（05~20）均依赖本埋点体系验证数据目标
- **下游被依赖方**：数据团队（离线消费 MySQL/Hive 埋点表）
- **数据流**：`tracker.ts` → `POST /events/batch` → `log/Kafka` → `MySQL/Hive` → BI 报表

---

## 六、边界场景

| 场景 | 处理 |
|------|------|
| 离线未上报 | 本地持久化，下次启动或网络恢复后补报 |
| 队列满 200 条 | 丢弃最早 50 条（FIFO 清理，优先保新事件）|
| 上报接口失败 | 重试 3 次（500ms/1s/2s 退避），失败保本地 |
| Apple 隐私合规 | iOS 用户默认不采集 IDFA（如需后续申请授权）|
| 微信小程序限制 | 不采集用户好友链信息（微信禁止）|

---

## 七、测试验收

| # | Given | When | Then |
|---|-------|------|------|
| T1 | 用户滑动 5 张卡片 | 每次滑动 | tracker 队列 +5 条 swipe 事件 |
| T2 | 队列满 10 条 | 立即 | 自动触发 flush，调 POST /events/batch |
| T3 | 断网状态 | 触发事件 | 事件保本地，不断网不上报 |
| T4 | 网络恢复 | 恢复连接 | 本地事件自动补报 |
| T5 | user_id 未登录时 | 触发事件 | event 中 user_id 为 null，仅含 device_id |
| T6 | 登录完成 | identify() | 后续事件带 user_id |
| T7 | App 杀进程重启 | 启动时 | 检测本地未上报队列并补报 |

---

## 八、UI 设计规范与功能边界

> 本节为样式与功能约束，开发时**必须遵守**，禁止自行发挥视觉风格或超出边界实现。

### 8.1 设计系统引用（强制）

| 规范层 | 来源 | 关键约束 |
|--------|------|---------|
| 品牌色 | `docs/AI-agent交接-品牌主色切换.md` | 埋点体系为纯数据层，**无独立 UI 页面**；涉及设置开关时走主题变量 |
| 组件 | `docs/需求文档/03-开发规范.md` §3 | 设置页开关复用 `u-switch`；**禁止重复造轮子** |
| 暗色模式 | `docs/需求文档/03-开发规范.md` | 埋点上报逻辑与 UI 无关，不受暗色模式影响 |

### 8.2 UI 约束说明

本 PRD 为纯前端数据采集层（tracker.ts），**不新增任何 UI 页面或组件**。埋点代码必须满足：

- 埋点上报**不得阻塞主线程**（异步 `setTimeout` / `requestIdleCallback`）
- 埋点上报**不得影响页面渲染性能**（禁止在 `onLoad` 同步发送）
- 埋点失败**静默降级**，禁止弹出错误提示干扰用户

### 8.3 必须实现

- [ ] tracker.ts 统一埋点入口，页面级 `onShow` / `onHide` / `onUnload` 自动上报
- [ ] 事件类型：page_view / click / swipe / exposure 四类
- [ ] 上报字段：userId / pagePath / eventType / timestamp / deviceId / extra(JSON)
- [ ] 异步上报，失败静默降级（本地缓存重试，最多 3 次）

### 8.4 禁止实现

- [ ] 禁止埋点上报阻塞主线程或影响首屏渲染
- [ ] 禁止埋点失败时弹 toast / modal 干扰用户
- [ ] 禁止采集用户隐私数据（聊天内容 / 位置精确坐标等，仅采集页面路径和事件类型）
- [ ] 禁止在非 tracker.ts 中散落埋点调用（统一走 tracker）

### 8.5 当前开发状态

> **就绪矩阵**：见 `docs/需求文档/23-功能就绪矩阵.md` FR-ENGINE → **待开发**
> **审查评级**：Phase 1 PRD 不在 21 号评级范围

---

## 九、变更记录

| 版本 | 日期 | 变更内容 |
|:---:|:---:|------|
| v1.0 | 2026-11-01 | 初版。前端 SDK MVP，服务端日志落盘，不上报实时，覆盖 5 大漏斗 35 个事件 |
