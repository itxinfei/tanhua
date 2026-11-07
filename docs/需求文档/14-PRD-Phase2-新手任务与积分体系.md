# PRD：新手 7 日任务 + 积分体系（Phase 2 P1）

> FR-ID: **FR-GROWTH-01**  
> 优先级：**P1**  
> 负责人：前端 1 人 + 后端 1 人  
> 预估工时：前端 1.5d + 后端 1d = 2.5d  
> 依赖项：兴趣选择页（获取 interest 相关任务条件）  
> FR-ID（需求文档关联）：所有 Phase 1 PRD 完成后配合上线

---

## 一、背景与目标

### 1.1 业务背景

新用户注册后缺少**阶段性目标**，不知道"下一步该做什么"。7 日留存仅 15%，用户感知不到自己的成长。

### 1.2 数据目标

| 指标 | 当前 | 目标 |
|------|:---:|:---:|
| 7 日留存 | 15% | 28%+ |
| 新用户 7 日内配对数 | 平均 2 次 | 平均 6+ 次 |
| 资料完整度提升（新用户）| 7 天保持 60% | 7 天提升至 85%+ |

### 1.3 非目标

- 不在本版本做积分商城（积分不兑换实物/虚拟商品，仅保留 → VIP 兑换通道）
- 不做排行榜（MVP 聚焦单人进度）

### 1.4 依赖项与调用链

- **前置依赖**：
  - FR-USER（兴趣选择页 → interest_save 事件 → 第 2 天任务触发）
  - 匹配页滑动事件（match_swipe_right → 第 3/5 天任务触发）
  - 编辑资料页（profile_complete → 第 1 天任务完成触发）
  - 配对服务（match_success → 第 7 天任务完成触发）
- **下游被依赖方**：FR-PAY-01（VIP 兑换 → vipService.grantDays）
- **数据流**：
  1. 用户完成具体行为 → 业务域发出 RocketMQ/tb_user_task_progress 更新
  2. 前端拉取 GET /users/tasks → 任务进度列表渲染
  3. 用户点击领取 → POST /users/tasks/{day}/claim → 积分增加 + 物品发放
  4. 积分兑换时 → 调用 vipService.grantDays → _days 字段更新

---

## 二、新手任务设计

### 2.1 7 日任务清单

| Day | 任务名称 | 任务描述 | 奖励积分 | 关联功能 |
|:---:|---------|---------|:---:|---------|
| D1 | 激活新手 | 完成首次登录 | 10 | 通用 |
| D2 | 兴趣达人 | 选择 ≥ 5 个兴趣标签（引导页只需选 3 个，Day 2 任务引导用户再额外选 2 个即可完成）| 20 | 兴趣选择页 |
| D3 | 社交新手 | 浏览 ≥ 30 张推荐卡 | 15 | 匹配页 |
| D4 | 主动出击 | 右滑（喜欢）≥ 10 次 | 20 | 匹配页 |
| D5 | 完美形象 | 资料完整度达到 80% | 30 | 编辑资料页 |
| D6 | 社区融入 | 发布第 1 条动态或评论 1 条 | 25 | 圈子页 |
| D7 | 牵手成功 | 获得第 1 次配对 | 50 | 匹配页 |

**总计最高积分：170**

### 2.2 任务进度条设计（独立组件 pages/mine/tasks.vue 或嵌入 mine/index.vue）

```
┌────────────────────────────────────────┐
│  🙋 新手任务  ·  7 日成长计划              │
│                                        │
│  Day 1 - 社交之旅开启                     │
│  [●] D1 · 激活新手           ✅ 已完成     │
│  [ ] D2 · 兴趣达人           20 积分      │
│  [ ] D3 · 社交新手           15 积分      │
│  [ ] D4 · 主动出击           20 积分      │
│  [ ] D5 · 完美形象           30 积分      │
│  [ ] D6 · 社区融入           25 积分      │
│  [ ] D7 · 牵手成功           50 积分      │
│                                        │
│  任务完成积分即可提现 VIP 体验装（需累计满 150 分）│
│                                        │
│  ┌─────────────────────────────┐       │
│  │  Day 2：选择兴趣标签，+20 分    │  ← 任务详情卡片
│  │  [去完成 →]                    │       │
│  └─────────────────────────────┘       │
│                                        │
└────────────────────────────────────────┘
```

### 2.3 积分奖励与变现（兑换规则）

| 累计积分 | 奖励内容 |
|:---:|------|
| 50 分 | 解锁"个性主题 · 1 周"（聊天气泡/主页背景）|
| 100 分 | 3 天 VIP 体验（解锁高级筛选 + 无限滑动）|
| 150 分 | 7 天 VIP 体验 |
| 每日登录 | +5 分（粘性奖励）|

---

## 三、技术实现

> **任务 total 字段说明**：Day1/D2/D6/D7 为 **触发式任务**（single-shot，触发即完成，total=1）；Day3/D4/D5 为 **渐进式任务**（accumulative，需累计到 total）。前端应根据任务类型选择展示快照进度条或累积进度条，避免 Day1 显示 "0/1"。

### 3.1 数据模型

```sql
-- 用户任务进度表
CREATE TABLE tb_user_task_progress (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    task_day     TINYINT NOT NULL COMMENT 'Day 1-7',
    task_name    VARCHAR(64) NOT NULL,
    status       TINYINT DEFAULT 0 COMMENT '0=未完成 1=可领取 2=已完成 3=已过期',
    progress     INT DEFAULT 0,
    total        INT DEFAULT 1,
    reward_score INT DEFAULT 0,
    completed_at DATETIME,
    created     DATETIME NOT NULL,
    
    UNIQUE uk_user_day(user_id, task_day),
    INDEX idx_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户积分表
CREATE TABLE tb_user_score (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL UNIQUE,
    total_score INT DEFAULT 0,
    used_score  INT DEFAULT 0,
    score_level TINYINT DEFAULT 1 COMMENT '积分等级(与创作者等级 creator_level 独立)',
    updated     DATETIME NOT NULL,
    
    INDEX idx_score(total_score DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.2 接口设计

| API | 描述 |
|-----|------|
| `GET /users/tasks` | 获取当前用户 7 日任务进度 |
| `POST /users/tasks/{day}/claim` | 领取特定 Day 的积分奖励 |
| `GET /users/score` | 获取用户积分 + 等级 |
| `GET /users/score/history` | 积分明细记录 |

### 3.3 并发安全

> 积分兑换涉及 `total_score` 扣减，必须使用乐观锁防并发超额兑换：

```java
// 兑换积分接口核心逻辑
@Transactional
public Result<Void> exchangeScore(Long userId, int score, String rewardType) {
    int affected = scoreMapper.deductScore(userId, score);
    // SQL: UPDATE tb_user_score SET used_score = used_score + #{score}, updated = NOW()
    //      WHERE user_id = #{userId} AND total_score - used_score >= #{score}
    if (affected == 0) throw new BusinessException(ErrorCode.SCORE_NOT_ENOUGH);
    vipService.grantDays(userId, rewardType);
    return Result.ok();
}
```

### 3.4 后端逻辑

```java
// 监听事件驱动任务进度更新
@EventListener
public void onEvent(UserEvent event) {
    switch(event.getType()) {
        case "interest_save": updateTask(event.getUserId(), 2, 1, 1); break;
        case "match_swipe_right": incrementProgress(event.getUserId(), 4); break;
        case "profile_complete": checkAndComplete(event.getUserId(), 5); break;
        case "feed_publish": updateTask(event.getUserId(), 6, 1, 1); break;
        case "match_success": updateTask(event.getUserId(), 7, 1, 1); break;
    }
}

// 每日定时任务：为注册第 N 天的用户解锁 Day N 任务
@Scheduled(cron = "0 0 0 * * ?")
public void unlockDailyTasks() {
    List<Long> users = userService.findUsersByRegisterDay(7);
    for (Long uid : users) {
        int day = calculateDaySinceRegister(uid);
        taskService.unlockTask(uid, day);
    }
}
```

### 3.5 接口错误响应

| 接口 | HTTP | 错误码 | 场景 | message |
|------|:---:|:---:|------|---------|
| `GET /users/tasks` | 401 | `AUTH_FAILED` | Token 过期 | "请先登录" |
| `POST /users/tasks/{day}/claim` | 401 | `AUTH_FAILED` | Token 过期 | "请先登录" |
| `POST /users/tasks/{day}/claim` | 409 | `ALREADY_CLAIMED` | 任务已领取 | "该任务奖励已领取" |
| `POST /users/tasks/{day}/claim` | 410 | `TASK_EXPIRED` | 任务已过期 | "该任务已过期" |
| `POST /users/tasks/{day}/claim` | 422 | `TASK_NOT_COMPLETED` | 任务未完成无法领取 | "请先完成任务目标" |
| `GET /users/score` | 401 | `AUTH_FAILED` | Token 过期 | "请先登录" |

> 前端统一处理：401 → 跳转登录；409/410/422 → toast + 禁用领取按钮。

---

## 四、边界场景

| 场景 | 处理 |
|------|------|
| 新用户 D1 之后注册 | 仅解锁已过期日（如 D3 注册的用户解锁 Day 3-7）|
| 任务超额完成 | 超出进度部分 progress=total，不重复奖励 |
| 用户跨日未登录 | 不补发已过期的任务，保持 status=0 |
| 积分扣减 | 仅在使用积分兑换奖励时扣减（本期不实现积分商城）|
| App 卸载重装 | 以 user_id 从服务端恢复任务进度 |

---

## 五、测试验收

| # | Given | When | Then |
|---|-------|------|------|
| T1 | Day1 新用户 | 注册完成 | D1 激活任务自动完成（不可手动完成）|
| T2 | Day2 | 选择 ≥ 5 个兴趣 | D2 任务状态 → 可领取 |
| T3 | Day4 | 右滑 10 次 | D4 进度 10/10 → 可领取 |
| T4 | 用户积分达 100 | 积分页显示 | 解锁 3 天 VIP 按钮可见 |
| T5 | VIP 兑换 | 点击兑换 | score 减少 + 开通 VIP（复用 VIP 开支付流程）|
| T6 | Day7 完成全部 | 连续完成 7 天任务 | 展示"7 日任务结业"成就弹窗 + 170 积分 |

---

## 六、UI 设计规范与功能边界

> 本节为样式与功能约束，开发时**必须遵守**，禁止自行发挥视觉风格或超出边界实现。

### 6.1 设计系统引用（强制）

| 规范层 | 来源 | 关键约束 |
|--------|------|---------|
| 品牌色 | `docs/AI-agent交接-品牌主色切换.md` | 进度条主渐变 135° 玫红→珊瑚；积分数字品牌色 `#c2185b`（浅底小字）；**禁止硬编码色值、禁用旧色 `#ff5e9c`** |
| 图标 | `docs/icon-icons.md` | 任务勾选用 `i-ph-check-circle`；积分用 `i-ph-coin`；兑换用 `i-ph-gift`；**禁止 emoji 充当图标** |
| 组件 | `docs/需求文档/03-开发规范.md` §3 | 进度条复用 `u-line-progress`；按钮复用 `a11y-button`；**禁止重复造轮子** |
| 暗色模式 | `docs/需求文档/03-开发规范.md` | 强制支持 |

### 6.2 页面级 UI 规格

| 元素 | 规格 |
|------|------|
| 任务进度条 | `u-line-progress`，高 16rpx，圆角 pill，主渐变填充，百分比文字 22rpx |
| 任务条目 | 行高 96rpx，左侧图标 48rpx + 任务名 28rpx + 右侧积分 `+5` 品牌色 24rpx |
| 任务状态 | 未完成：ghost 按钮"去完成"；已完成：`i-ph-check-circle` 绿色 `#22c55e` |
| 积分余额 | 页面顶部，`i-ph-coin` 金色 + 数字 36rpx bold `#c2185b` |
| 兑换卡片 | 网格 2 列，圆角 `$uni-border-radius-lg(24rpx)`，商品图 + 名称 + 积分价 + 兑换按钮 |

### 6.3 必须实现

- [ ] 7 日任务清单：每日 1 个核心任务（完善资料/上传头像/发布动态等）
- [ ] 任务进度条：7 日总进度 + 当日任务状态
- [ ] 积分奖励：任务完成即时发放 + 推送通知
- [ ] 积分兑换：VIP 天数 / 超级喜欢 / Boost（对齐 15 号消耗品体系）
- [ ] 并发安全：积分发放防重复（幂等键 taskId+userId+date）
- [ ] 暗色模式适配

### 6.4 禁止实现

- [ ] 禁止 emoji 充当任务/积分图标
- [ ] 禁止硬编码品牌色值
- [ ] 禁止积分兑换为现金/实物（仅平台虚拟权益）
- [ ] 禁止任务重复发放积分（幂等校验）
- [ ] 禁止强制完成全部任务（可跳过，但无积分）

### 6.5 当前开发状态

> **就绪矩阵**：见 `docs/需求文档/23-功能就绪矩阵.md` FR-GROWTH-01 → **待开发**
> **审查评级**：见 `docs/需求文档/21-PRD审查报告-Phase2&3.md` → **B+**

---

## 七、变更记录

| 版本 | 日期 | 变更内容 |
|:---:|:---:|------|
| v1.0 | 2026-11-01 | 初版。7 日任务清单 + 事件驱动进度更新 + 积分奖励（50/100/150 三档换 VIP）|
