# PRD：邀请好友得 VIP 系统（Phase 3 P1）

> FR-ID: **FR-GROWTH-02**  
> 优先级：**P1**  
> 负责人：前端 1 人 + 后端 1 人  
> 预估工时：前端 1.5d + 后端 1.5d = 3d  
> 依赖项：VIP 套餐模型（已有）、分享能力  
> FR-ID（需求文档关联）：FR-PAY-01（VIP 会员）

---

## 一、背景与目标

### 1.1 业务背景

当前获客纯靠广告投放，CAC（用户获取成本）高。陌生人社交天然适合病毒传播——用户愿意邀请朋友一起使用、分享自己的匹配炫耀。

**核心目标**：建立"邀请-注册-兑换奖励"闭环，让 K 因子达到 0.3+。

### 1.2 数据目标

| 指标 | 当前 | 目标 |
|------|:---:|:---:|
| K 因子（病毒系数）| 0 | 0.3+ |
| 用户月均邀请发起数 | 0 | 2+ 次 |
| 邀请注册转化率 | — | 15%+ |
| 邀请贡献的新用户占比 | 0% | 20%+ |

---

## 二、邀请体系设计

### 2.1 奖励阶梯

| 阶梯 | 邀请人数 | 邀请人奖励 | 被邀请人奖励 |
|:---:|:---:|------|------|
| 🥉 铜牌 | 1 人 | 3 天 VIP + 1 次超级喜欢 | 3 天 VIP |
| 🥈 铜牌 | 3 人 | 7 天 VIP + 5 次超级喜欢 + 1 次 Boost | — |
| 🥇 金牌 | 10 人 | 30 天 VIP + 10 次 Boost + 1 次推荐位曝光 | — |
| 💎 钻石 | 50 人 | 90 天 VIP + 永久"邀请达人"徽章 + 专属客服 | — |

### 2.2 邀请流程

```
邀请人：
  My → 邀请好友 → 生成带 inviteCode 的分享卡片
    │
    ├─ 微信小程序：转发卡片到聊天（onShareAppMessage）
    │   卡片内容："我在心飞交友发现了好多有趣的人，快来看看！🎉"
    │   链接：/pages/auth/login?inviteCode=ABC123
    │
    └─ H5：复制链接 or 生成海报
        poster：用户头像 + "邀请你来心飞交友" + 二维码

被邀请人：
  点击分享卡片/链接 → /pages/auth/login?inviteCode=ABC123
    │
    ├─ 未注册：注册流程自动携带 inviteCode → 注册完成 → 绑定邀请关系
    │
    └─ 已注册：toast"你已经注册过了" → 忽略

邀请人奖励发放：
  被邀请人注册完成 → 后端调 rewardInvite(inviterId)
  → 累计邀请人数 +1 → 检查阶梯 → 发放奖励（VIP 天数 + 超级喜欢次数）
  → 推送通知邀请人："🎉 你邀请的好友已注册，累计已邀请 N 人"
```

---

## 三、分享组件设计

### 3.1 邀请分享页（pages/mine/invite.vue）

```
┌────────────────────────────────────────┐
│                                        │
│           🎉 邀请好友 得 VIP             │
│                                        │
│     邀请 1 位好友注册，立得 3 天 VIP       │
│                                        │
│  ┌──────────────────────────────────┐  │
│  │  🥉 邀请 1 人  ·  3天 VIP   [已领] │  │
│  │  🥈 邀请 3 人  ·  7天 VIP   2/3   │  │  ← 进度条
│  │  🥇 邀请 10 人 ·  VIP月卡  2/10   │  │
│  │  💎 邀请 50 人 ·  年卡      2/50   │  │
│  └──────────────────────────────────┘  │
│                                        │
│  邀请码：XF9KZ2  (点击复制)              │
│                                        │
│  ┌──────────────────────────────────┐  │
│  │      📤 分享到微信                 │  │
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────────┐  │
│  │      🖼 生成邀请海报               │  │
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────────┐  │
│  │      🔗 复制邀请链接              │  │
│  └──────────────────────────────────┘  │
│                                        │
│  邀请记录                               │
│  ┌──────────────────────────────────┐  │
│  │ 🍎 小王  ·  注册成功 ·  2026-11-01  │  │
│  │ 🍊 小李  ·  注册成功 ·  2026-10-28  │  │
│  └──────────────────────────────────┘  │
│                                        │
└────────────────────────────────────────┘
```

---

## 四、数据模型与接口

### 4.1 数据模型

```sql
-- 邀请关系表
CREATE TABLE tb_invite (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    inviter_id    BIGINT NOT NULL COMMENT '邀请人ID',
    invitee_id    BIGINT NOT NULL COMMENT '被邀请人ID（注册后回填）',
    invite_code   VARCHAR(16) NOT NULL,
    status        TINYINT DEFAULT 0 COMMENT '0=点击 1=注册完成 2=奖励已发',
    reward_level  TINYINT DEFAULT 0 COMMENT '0=未达阶梯 1=铜牌 2=银牌 3=金牌 4=钻石',
    created_at    DATETIME NOT NULL,
    completed_at  DATETIME,
    
    UNIQUE uk_invitee(invitee_id),
    INDEX idx_inviter(inviter_id),
    INDEX idx_code(invite_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户表新增字段
ALTER TABLE tb_user ADD COLUMN invite_code VARCHAR(16) UNIQUE COMMENT '本人邀请码';
ALTER TABLE tb_user ADD COLUMN inviter_id BIGINT DEFAULT 0 COMMENT '邀请人ID';
```

### 4.2 接口设计

| API | 描述 |
|-----|------|
| `GET /users/invite/info` | 我的邀请信息（邀请数、阶梯进度、记录）|
| `GET /users/invite/rewards` | 邀请奖励记录 |
| `POST /users/invite/generate` | 生成邀请码（新用户首次调用）|
| `GET /auth/invite-code/:code` | 校验邀请码有效性 |

### 4.3 后端核心逻辑

```java
// 新用户注册完成后绑定邀请关系
@AfterRegister
public void bindInvite(Long userId, String inviteCode) {
    if (StringUtils.isBlank(inviteCode)) return;
    Long inviterId = userService.findByInviteCode(inviteCode);
    if (inviterId == null || inviterId.equals(userId)) return;
    
    // 写入邀请关系
    inviteService.create(inviterId, userId, inviteCode);
    
    // 累计邀请人数 +1
    int totalInvites = inviteService.countByInviter(inviterId);
    
    // 检查是否达阶梯并发放奖励
    InviteLevel reachedLevel = InviteLevel.check(totalInvites);
    if (reachedLevel != null && !inviteService.isRewarded(inviterId, reachedLevel)) {
        // 发放奖励
        rewardService.grantInviteReward(inviterId, reachedLevel);
        inviteService.markRewarded(inviterId, reachedLevel);
        
        // 推送通知
        pushService.push(inviterId, "🎉 恭喜达成" + reachedLevel.getLabel() + "，" + reachedLevel.getRewardDesc(), 
                        PushType.INVITE_REACH, null);
    }
}
```

---

## 五、防作弊策略

| 作弊手段 | 防御 |
|---------|------|
| 自邀请（自己注册新号）| 手机号 + 设备指纹去重，同一设备 30 天内只能算 1 次邀请 |
| 伪造邀请 | invite_code 与服务端绑定校验，伪造 code 无法绑定 |
| 批量刷邀请 | 同一 IP 每小时最多 10 次邀请注册 → 风控标志 |
| 机刷行为 | 被邀请用户需完成注册 + 首次登录才能计入 + 保留设备指纹 |
| 羊毛党（注册后立即注销）| 邀请人 VIP 奖励设置 3 天观察期（被邀请人注销则扣回）|

---

## 六、测试验收

| # | Given | When | Then |
|---|-------|------|------|
| T1 | 用户 A 生成邀请码 | 首次访问邀请页 | 生成唯一 6 位码（字母+数字）|
| T2 | 用户 B 通过 A 邀请码注册 | 注册完成后 | tb_invite 新增记录，status=1 |
| T3 | A 累计邀请 1 人 | 注册完成 | A 获得 3 天 VIP（可查询 VIP 到期时间延长）+ 推送通知 |
| T4 | A 累计邀请 3 人 | 阶梯升级 | A 获得银牌奖励（7 天 VIP + 5 超级喜欢）+ 全屏庆祝 |
| T5 | 自邀请（同设备新号）| 注册 | 不计入邀请数 + toast"自己不能邀请自己" |
| T6 | H5 端复制邀请链接 | 点击复制 | 链接格式为 https://h5.xfjy.com/?inviteCode=ABC123 / 小程序链接 route：/pages/auth/login?inviteCode=ABC123 |
| T7 | 邀请人 VIP 到期后邀请奖励 | 奖励 VIP 天数叠加 | 当前到期日 + 3 天（续费逻辑同 VIP 续费）|

---

## 七、变更记录

| 版本 | 日期 | 变更内容 |
|:---:|:---:|------|
| v1.0 | 2026-11-01 | 初版。邀请 4 阶梯奖励体系（3天/7天/30天/90天 VIP），防作弊（设备去重+IP限频），小程序分享卡片 + H5 海报生成 |
