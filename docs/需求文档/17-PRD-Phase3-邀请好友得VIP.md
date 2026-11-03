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

### 1.3 非目标

- 不在本版本实现邀请链多级分佣（如 A 邀请 B、B 邀请 C，A 也获得奖励）
- 不做邀请排行榜
- 不引入裂变海报个性化定制（使用统一模板）

### 1.4 依赖项与调用链

- **前置依赖**：
  - FR-PAY-01（VIP 套餐模型 → 奖励发放时 vipService.grantDays 调用）
  - 推送服务 pushService.push（奖励发放后推送通知）
  - 风控/设备指纹服务（反作弊判定，依赖外部设备指纹 SDK）
  - 定时任务调度（@Scheduled 02:00 注销扫描 + 奖励扣回）
- **下游被依赖方**：无
- **数据流**：
  1. 邀请人分享 inviteCode → 被邀请人注册时携带 code
  2. 注册完成 → 后端校验 code + 反作弊（设备指纹 + IP 限流）
  3. 校验通过 → tb_invite 写入 status=1 + 奖励累加
  4. 每阶梯达标 → 定时任务触发 vipService.grantDays + push 通知

---

## 二、邀请体系设计

### 2.1 奖励阶梯

| 阶梯 | 邀请人数 | 邀请人奖励 | 被邀请人奖励 |
|:---:|:---:|------|------|
| 🥉 铜牌 | 1 人 | 3 天 VIP + 1 次超级喜欢 | 3 天 VIP |
| 🥈 银牌 | 3 人 | 7 天 VIP + 5 次超级喜欢 + 1 次 Boost | — |
| 🥇 金牌 | 10 人 | 30 天 VIP + 10 次 Boost + 1 次推荐位曝光 | — |
| 💎 钻石 | 50 人 | 90 天 VIP + 永久"邀请达人"徽章 + 专属客服 | — |

> **推荐位曝光定义**：金牌奖励中的"1 次推荐位曝光"指被邀请人的主页卡片在匹配页（match）获得一次置顶展示机会，展示时长 24 小时，到期自动失效。该奖励由后端在达成阶梯时写入 `tb_user_item_stock`（item_type=RECOMMEND_EXPOSURE），前端在匹配页加载时检查库存并在卡片堆顶插入。本期（Phase 3）仅记录库存，前端展示逻辑待 Phase 4 实现。

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
    status        TINYINT DEFAULT 0 COMMENT '0=点击 1=注册完成 2=奖励已发 3=已失效/扣回',
    reward_level  TINYINT DEFAULT 0 COMMENT '0=未达阶梯 1=铜牌 2=银牌 3=金牌 4=钻石',
    created       DATETIME NOT NULL,
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

### 4.4 接口错误响应

| 接口 | HTTP | 错误码 | 场景 | message |
|------|:---:|:---:|------|---------|
| `GET /users/invite/info` | 401 | `AUTH_FAILED` | Token 过期 | "请先登录" |
| `GET /users/invite/rewards` | 401 | `AUTH_FAILED` | Token 过期 | "请先登录" |
| `POST /users/invite/generate` | 409 | `CODE_EXISTS` | 用户已有推荐码 | "邀请码已存在" |
| `GET /auth/invite-code/:code` | 404 | `CODE_NOT_FOUND` | 邀请码不存在 | "无效的邀请码" |
| `GET /auth/invite-code/:code` | 410 | `INVITER_DISABLED` | 邀请人账号已注销 | "邀请人不存在" |

> 前端统一处理：401 → 跳转登录；404/410 → toast + 隐藏邀请码绑定输入框；409 → 返回已有邀请码复用。

---

## 五、防作弊策略

| 作弊手段 | 防御 |
|---------|------|
| 自邀请（自己注册新号）| 手机号 + 设备指纹去重，同一设备 30 天内只能算 1 次邀请 |
| 伪造邀请 | invite_code 与服务端绑定校验，伪造 code 无法绑定 |
| 批量刷邀请 | 同一 IP 每小时最多 10 次邀请注册 → 风控标志 |
| 机刷行为 | 被邀请用户需完成注册 + 首次登录才能计入 + 保留设备指纹（⚠ 依赖设备指纹采集功能上线）|
| 羊毛党（注册后立即注销）| 邀请人 VIP 奖励设置 3 天观察期（被邀请人注销则扣回，定时任务每日 02:00 扫描 + 触发式回滚）|

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
| T8 | 被邀请人注销（3 天观察期内）| 定时任务检测到注销 | 扣回邀请人对应 VIP 天数 + tb_invite.status → 3 |

---

## 七、UI 设计规范与功能边界

> 本节为样式与功能约束，开发时**必须遵守**，禁止自行发挥视觉风格或超出边界实现。

### 7.1 设计系统引用（强制）

| 规范层 | 来源 | 关键约束 |
|--------|------|---------|
| 品牌色 | `docs/AI-agent交接-品牌主色切换.md` | 阶梯进度条主渐变；奖牌金 `#f5c542`（铜银金钻用不同明度）；分享卡片主渐变背景；**禁止硬编码色值、禁用旧色 `#ff5e9c`** |
| 图标 | `docs/icon-icons.md` | 奖牌用 `i-ph-medal`；分享用 `i-ph-share-network`；邀请用 `i-ph-user-plus`；**禁止 emoji 充当图标**（🥉🥈🥇💎→`i-ph-medal` 不同色） |
| 组件 | `docs/需求文档/03-开发规范.md` §3 | 按钮复用 `a11y-button`；进度条复用 `u-line-progress`；**禁止重复造轮子** |
| 暗色模式 | `docs/需求文档/03-开发规范.md` | 强制支持 |

### 7.2 页面级 UI 规格

| 元素 | 规格 |
|------|------|
| 邀请页头部 | 主渐变背景，邀请码大字 48rpx bold 白字 + 复制按钮 |
| 阶梯进度条 | `u-line-progress`，4 档（铜1/银3/金10/钻50），当前档高亮 |
| 阶梯卡片 | 横向滚动，宽 240rpx，圆角 `$uni-border-radius-lg(24rpx)`，奖牌图标 + 人数 + 奖励列表 |
| 分享按钮 | `a11y-button` type="primary"，高 88rpx，圆角 pill，"邀请好友" |
| 分享卡片 | 海报：用户头像 + "邀请你来心飞交友" + 二维码，主渐变边框 |
| 邀请记录 | `u-cell` 列表：被邀请人头像 + 昵称 + 状态（已注册/已奖励） |

### 7.3 必须实现

- [ ] 邀请页：邀请码 + 阶梯进度 + 分享按钮 + 邀请记录
- [ ] 分享：微信小程序卡片转发 + H5 复制链接/生成海报
- [ ] 阶梯奖励：铜(1人)/银(3人)/金(10人)/钻(50人)，叠加发放
- [ ] 反作弊：设备指纹 24h 内同设备注册视为无效
- [ ] 邀请记录状态：0=点击 / 1=注册完成 / 2=奖励已发 / 3=已失效扣回
- [ ] 暗色模式适配

### 7.4 禁止实现

- [ ] 禁止 emoji 充当奖牌/分享图标
- [ ] 禁止硬编码品牌色值
- [ ] 禁止无上限邀请（设备指纹反作弊必须生效）
- [ ] 禁止奖励直接兑换现金（仅 VIP 天数/Boost/超级喜欢等虚拟权益）
- [ ] 禁止钻级"专属客服"自动分配（需运营手动）

### 7.5 当前开发状态

> **就绪矩阵**：见 `docs/需求文档/23-功能就绪矩阵.md` FR-GROWTH-02 → **待开发**
> **审查评级**：见 `docs/需求文档/21-PRD审查报告-Phase2&3.md` → **B+**
> **推荐位曝光**：金牌奖励"1 次推荐位曝光"本期仅记录库存，前端展示待 Phase 4

---

## 八、变更记录

| 版本 | 日期 | 变更内容 |
|:---:|:---:|------|
| v1.0 | 2026-11-01 | 初版。邀请 4 阶梯奖励体系（3天/7天/30天/90天 VIP），防作弊（设备去重+IP限频），小程序分享卡片 + H5 海报生成 |
| v1.1 | 2026-11-02 | 修正阶梯名称冲突（铜牌重复 → 铜/银/金/钻四级对齐 reward_level 枚举）；新增 tb_invite.status=3 已失效/扣回状态；补充设备指纹依赖标注；补充 VIP 扣回流程（定时任务 02:00 扫描 + 触发式回滚）|
