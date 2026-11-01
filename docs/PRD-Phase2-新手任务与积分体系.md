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

- 不在本版本做积分商城（积分仅做心理激励，不实际兑换）
- 不做排行榜（MVP 聚焦单人进度）

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

### 2.3 并发安全

> 积分兑换涉及 `total_score` 扣减，必须使用乐观锁防并发超额兑换：

```java
// 兑换积分接口核心逻辑（悲观锁示例）
@Transactional
public Result<Void> exchangeScore(Long userId, int score, String rewardType) {
    int affected = scoreMapper.deductScore(userId, score);
    // SQL: UPDATE tb_user_score SET used_score = used_score + #{score}, updated_at = NOW()
    //      WHERE user_id = #{userId} AND total_score - used_score >= #{score}
    if (affected == 0) throw new BusinessException(ErrorCode.SCORE_NOT_ENOUGH);
    vipService.grantDays(userId, rewardType);
    return Result.ok();
}
```

### 2.4 积分奖励与变现

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
    status       TINYINT DEFAULT 0 COMMENT '0=未完成 1=可领取 2=已完成',
    progress     INT DEFAULT 0,
    total        INT DEFAULT 1,
    reward_score INT DEFAULT 0,
    completed_at DATETIME,
    created_at   DATETIME NOT NULL,
    
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
    updated_at  DATETIME NOT NULL,
    
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

### 3.3 后端逻辑

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

## 六、变更记录

| 版本 | 日期 | 变更内容 |
|:---:|:---:|------|
| v1.0 | 2026-11-01 | 初版。7 日任务清单 + 事件驱动进度更新 + 积分奖励（50/100/150 三档换 VIP）|
