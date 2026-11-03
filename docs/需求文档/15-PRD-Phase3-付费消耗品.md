# PRD：付费消耗品 - 超级喜欢 / Boost 曝光（Phase 3 P2）

> FR-ID: **FR-PAY-06**  
> 优先级：**P2**  
> 负责人：前端 1 人 + 后端 1 人  
> 预估工时：前端 1d + 后端 1d = 2d  
> 依赖项：VIP 套餐（已有）、埋点体系（已有）  
> FR-ID（需求文档关联）：FR-PAY-01（VIP 套餐）、FR-PAY-06（消耗品订单）/ FR-REC-02（探花匹配）

---

## 一、背景与目标

### 1.1 业务背景

当前付费仅有 VIP 月订阅（¥19.8-39.8/月），**缺少低门槛消耗品付费场景**。大量用户（占 DAU 60%）处于"还没感受到 VIP 值不值"的阶段，直接用月订阅墙住太早。

**核心策略**：提供 0.99-19.9 元的低门槛付费道具，让免费用户也能小额付费体验高级功能→养成付费习惯→后续转化 VIP。

### 1.2 数据目标

| 指标 | 当前 | 目标 |
|------|:---:|:---:|
| 付费渗透率（DAU 中付费用户占比）| <1% | 3%+ |
| ARPPU（每付费用户平均收入）| ¥30（仅 VIP 月订阅）| ¥25+（含消耗品）|
| 消耗品付费用户 30 日内 VIP 转化率 | — | 20%+ |

### 1.3 非目标

- 不在本版本上虚拟礼物商城（P3 优先级）
- 不做限时折扣等营销活动
- 不做团购/拼团等社交裂变付费

---

## 二、消耗品设计

### 2.1 三大消耗品

| 消耗品 | 定价 | 描述 | 适用场景 | 心理驱动 |
|--------|:---:|------|---------|---------|
| 💕 **超级喜欢** | ¥0.99 / 个 或 ¥9.9 / 10 个 | 在对方卡片显示超级喜欢动画 + 立即通知对方 | "我很想认识这个人" | 表达主动 + FOMO |
| 🚀 **Boost 曝光** | ¥4.99 / 30min 或 ¥19.9 / 次日 | 30min（或次日）内你的推荐排名加权 +100% | "我希望更多人被看到" | 效率驱动 + 虚荣心 |
| 🕐 **滑动次数补充包** | ¥0.99 / 20 次 ¥4.99 / 100 次 | 一次性增加滑动次数（不计入每日 50 次）| "我刚才不该左滑那个人" | 补偿心态 + 即时满足 |

### 2.2 消耗品入口设计

```
匹配页底部 TabBar 右侧新增按钮

┌────────────────────────────────────────┐
│                     💕 超级喜欢 2    │  ← 右滑时顶部出现
│                     🚀 Boost 1      │
│  [卡片区域]        [补充包 1 包]     │
│                     [VIP 购买]       │
│                                        │
│                      已购消耗品        │
│                  ┌──────────────────┐  │
│                  │💕 超级喜欢 (2)    │  │
│                  │🚀 Boost (1)      │  │  ← 已购买但未使用
│                  │📦 次数包 (1)      │  │
│                  └──────────────────┘  │
```

---

## 三、服务归属与数据模型

> ⚠ **服务边界对齐**：消耗品表（`tb_item_package` / `tb_user_item_stock`）归 **xfjy-pay-service** 管辖。server 通过 PayApi 内部接口访问，**不直接持有 Mapper**，符合 `CONST-ARCH-004` 跨服务边界约束。

### 3.1 消耗品订单模型

消耗品购买**复用 `tb_pay_order` 表**，通过 `biz_type` 字段区分：`biz_type=VIP` 表示 VIP 订阅，`biz_type=ITEM` 表示消耗品。消耗品订单额外字段存入 `biz_extra`（JSON 格式：`{"itemType":"SUPER_LIKE","quantity":1}`）。

### 3.2 订单状态机

```
PENDING ──[支付成功]──→ PAID ──[使用消耗品]──→ USED
   │                        │
   └──[30 分钟超时]──→ EXPIRED    └──[退款]──→ REFUNDED
```

### 3.3 流程（商品与库存表 DDL 见 §5.1 接口出入参）

```
1. 用户在匹配页看到心仪卡片
2. 底部 TabBar 中央或右滑按钮触发超级喜欢弹窗
3. 选择超级喜欢 → POST /pay/super-like （消耗 1 个库存）
4. 成功返回 → 卡片展示专属动画（金色气泡飞出 + 大爱心 + "超级喜欢 💕" 文案）
5. 对方在推荐卡池中优先看到超级喜欢通知（卡片右上角带 💕 角标）
6. 对方也右滑 → 立即弹窗"你们互相超级喜欢！"（区别于普通匹配弹窗）
```

### 3.4 超级喜欢动画

```
普通匹配：两头像靠近 + 红色爱心爆破
超级喜欢：两头像靠近 + 金色相框 + 彩虹光效 + "💕 超级喜欢 💕" + 星星粒子
                                     + 音效不同（叮当 vs 铃声）
```

### 3.5 超级喜欢权益对比

| 权益 | 普通喜欢 | 超级喜欢 |
|------|:---:|:---:|
| 卡片中展示 | ✓ | ✓（金色标识）|
| 对方通知 | 配对成功后通知 | 即时通知："有人超级喜欢你" |
| 配对成功率 | 正常 1x | 对方 Card 堆顶 + 3x 权重 |
| 对方可跳过 | ✓ | ✓ |

---

## 四、Boost 曝光详细设计

### 4.1 流程

```
1. 用户购买 Boost（¥4.99/30min 或 ¥19.9/次日）→ 消耗 1 个库存
2. 后端标记用户 boostExpiresAt = now + 30min
3. 推荐算法判断：if user.boostExpiresAt > now → recommendWeight += 100%
4. 前端显示 Boost 倒计时（"🚀 Boost 中 · 还剩 22 分钟"）
5. Boost 期间用户主页和卡池显示"Boost 中"角标
6. 到期后推送提醒："🚀 Boost 已结束，本时段你被 47 人看到，额外获得 3 对喜欢"
```

### 4.2 Boost 叠加规则

叠加公式：新到期时间 = MAX(当前 Boost 到期时间, now) + 增量时长。

示例：用户已有 Boost 至 12:30，再购买 30min → 新到期 = 12:30 + 30min = 13:00（非 12:30 → 13:00 = 仅 30 分钟有效时间）。

```
Boost 结束时推送数据：
  "🚀 Boost 时段已结束
   曝光量：247 次（+196%）
   新增喜欢：3 人
   新增粉丝：1 人
   继续 Boost？ ¥4.99/30min"
```

---

## 五、购买与支付

### 5.1 商品模型

```sql
-- 消耗品商品表（新增）
CREATE TABLE tb_item_package (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_type    VARCHAR(16) NOT NULL COMMENT 'SUPER_LIKE / BOOST / SWIPE_PACK',
    name         VARCHAR(64) NOT NULL,
    quantity     INT NOT NULL COMMENT '数量（超级喜欢个数/小时数/次数）',
    price        DECIMAL(10,2) NOT NULL,
    is_active    TINYINT DEFAULT 1,
    sort_order   INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始化数据
INSERT INTO tb_item_package (item_type, name, quantity, price, sort_order) VALUES
    ('SUPER_LIKE', '超级喜欢 × 1', 1, 0.99, 1),
    ('SUPER_LIKE', '超级喜欢 × 10', 10, 9.90, 2),
    ('BOOST', 'Boost 30min', 30, 4.99, 3),
    ('BOOST', 'Boost 次日', 1440, 19.90, 4),
    ('SWIPE_PACK', '滑动次数 × 20', 20, 0.99, 5),
    ('SWIPE_PACK', '滑动次数 × 100', 100, 4.99, 6);

-- 用户消耗品库存表
CREATE TABLE tb_user_item_stock (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    item_type   VARCHAR(16) NOT NULL,
    quantity    INT NOT NULL DEFAULT 0,
    updated     DATETIME NOT NULL,
    
    UNIQUE uk_user_item(user_id, item_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.2 购买接口

> ⚠ **BFF 层与内部 API 映射**：以下路径为 xfjy-server（BFF 层）暴露给前端的 RESTful 路径。server 内部通过 `@HttpExchange` 调用 xfjy-pay-service 的内部 API（`/pay/item/*`），不直接操作 pay-service 的数据库表。

| BFF 层路径（xfjy-server） | 内部 API（xfjy-pay-service） | 描述 |
|-----|------|------|
| `GET /users/vip/item/packages` | `GET /pay/item/packages` | 查询所有消耗品商品 |
| `POST /users/vip/item/order` | `POST /pay/item/order` | 购买消耗品（复用 VIP 支付通道，调用同一沙箱支付）|
| `GET /users/vip/item/stock` | `GET /pay/item/stock` | 查询用户当前库存 |
| `POST /users/vip/item/use` | `POST /pay/item/use` | 使用消耗品（扣除 1 个库存，返回使用结果）|

### 5.3 使用逻辑

```
POST /users/vip/item/use
Request: { "itemType": "SUPER_LIKE", "targetUserId": 1234 }

后端逻辑：
  1. 校验库存 tb_user_item_stock WHERE user_id AND item_type → quantity > 0
  2. 库存 - 1
  3. 记录使用日志
  4. 触发业务效果（如：给对方发推送、为卡片加权）
  5. 返回使用结果

错误码：
  - 库存不足：40001，前端引导购买
  - 消费失败：40002
```

---

## 六、边界场景

| 场景 | 处理 |
|------|------|
| 库存不足时触发使用 | 弹出购买弹窗（建议使用完随时购买）|
| Boost 期间再买 Boost | 返回错误"Boost 中不可叠加购买"，或使用时间叠加（本期：叠加）|
| 超级喜欢自己已经右滑过的人 | 允许（对方知悉你特别喜欢）|
| 消费中支付失败 | 库存不扣，仅支付成功后扣库存 |
| VIP 用户购买超级喜欢 | VIP 每月赠送 5 个超级喜欢（默认），本功能仍然可用 |
| 超级喜欢已购买但未使用 | 永久储存在 tb_user_item_stock（除 Boost 时效外），用户可下次使用 |

---

## 七、统一错误码与异常响应

> 本 PRD 接口错误码汇总（部分已在 5.3 节使用）：

| 接口 | HTTP | 业务码 | 场景 | message |
|------|:---:|:---:|------|---------|
| `GET /users/vip/item/packages` | 401 | `AUTH_FAILED` | Token 过期 | "请先登录" |
| `POST /users/vip/item/order` | 400 | `INVALID_ITEM` | 消耗品 ID 不存在 | "商品不存在" |
| `POST /users/vip/item/order` | 402 | `PAYMENT_REQUIRED` | 支付失败 | "支付失败，请重试" |
| `POST /users/vip/item/order` | 409 | `DUPLICATE_ORDER` | 订单重复提交 | "订单已提交，请勿重复" |
| `GET /users/vip/item/stock` | 401 | `AUTH_FAILED` | Token 过期 | "请先登录" |
| `POST /users/vip/item/use` | 400 | `INVALID_TARGET` | 目标用户不存在 | "目标用户不存在" |
| `POST /users/vip/item/use` | 402 | `STOCK_EMPTY` | 库存不足 | "库存不足，请先购买" |
| `POST /users/vip/item/use` | 403 | `COOLDOWN` | 冷却中（Boost 叠加期） | "当前已有生效中的 Boost" |

> 前端统一处理：401 → 跳转登录；402 → toast + 刷新库存；409 → 幂等处理（展示已有订单）；400/403 → toast + 禁用按钮。

---

## 八、测试验收

| # | Given | When | Then |
|---|-------|------|------|
| T1 | 无库存 | 右滑触发超级喜欢 | 弹出购买弹窗："需要 1 个超级喜欢（¥0.99）" |
| T2 | 有库存 1 个 | 使用超级喜欢 | 库存 -1，卡片金色动画，对方收到即时推送 |
| T3 | 未购买 Boost | 点击 Boost | 引导购买 ¥4.99/30min |
| T4 | 购买 Boost | 匹配页区域 | 显示 Boost 倒计时 + 角标 |
| T5 | BOOST 到期 | 到期时 | 推送反馈总结：曝光 247 次 + 3 个喜欢 |
| T6 | 购买滑动次数包 | 使用 10 次 | 使用完毕 +10 次，不改变每日剩余 |
| T7 | 购买成功后断网 | 后补单逻辑 | 后端重复通知 + 幂等 |

---

## 九、UI 设计规范与功能边界

> 本节为样式与功能约束，开发时**必须遵守**，禁止自行发挥视觉风格或超出边界实现。

### 9.1 设计系统引用（强制）

| 规范层 | 来源 | 关键约束 |
|--------|------|---------|
| 品牌色 | `docs/AI-agent交接-品牌主色切换.md` | 超级喜欢金色标识 `#f5c542`（VIP 金，非品牌色）；购买按钮主渐变；**品牌色与 VIP 金不混淆；禁止硬编码色值** |
| 图标 | `docs/icon-icons.md` | 超级喜欢用 `i-ph-heart` 金色；Boost 用 `i-ph-rocket`；滑动包用 `i-ph-cards`；**禁止 emoji 充当图标**（💕→`i-ph-heart`） |
| 组件 | `docs/需求文档/03-开发规范.md` §3 | 购买按钮复用 `a11y-button`；套餐卡片圆角 `$uni-border-radius-lg(24rpx)`；**禁止重复造轮子** |
| 暗色模式 | `docs/需求文档/03-开发规范.md` | 强制支持 |
| 附录 B | `docs/需求文档/01-需求文档.md` 附录 B | B4 VIP 失败必须错误态 + 重试（消耗品购买同理） |

### 9.2 页面级 UI 规格

| 元素 | 规格 |
|------|------|
| 消耗品入口 | 匹配页右上角 `i-ph-heart` 金色 + 库存角标 |
| 套餐卡片 | 网格 2 列，圆角 `$uni-border-radius-lg(24rpx)`，图标 64rpx + 名称 + 价格 36rpx bold |
| 超级喜欢动画 | 两头像 + 金色相框 + 彩虹光效 + 星星粒子，时长 1.5s，**禁止超过 2s** |
| 库存展示 | "剩余 N 次" 22rpx `--color-text-secondary` |
| 购买失败 | 错误态 + 重试按钮（附录 B4 同款） |
| 使用确认弹窗 | 居中弹窗，"确认使用 1 次超级喜欢？" + 确认/取消 |

### 9.3 必须实现

- [ ] 三大消耗品：超级喜欢 / Boost / 滑动包（价格 0.99-19.9 元）
- [ ] 套餐购买：微信支付，复用 tb_pay_order + biz_type=ITEM
- [ ] 使用逻辑：超级喜欢即时使用 / Boost 30min 倒计时 / 滑动包扣次数
- [ ] 库存查询 + 使用接口（幂等，防重复扣减）
- [ ] 购买失败错误态 + 重试
- [ ] 暗色模式适配

### 9.4 禁止实现

- [ ] 禁止 emoji 充当消耗品图标
- [ ] 禁止硬编码品牌色值
- [ ] 禁止消耗品退款自助操作（REFUNDED 仅客服人工触发）
- [ ] 禁止 Boost 叠加计算用 now+increment（必须 MAX(current_expiry, now)+increment）
- [ ] 禁止超级喜欢动画超过 2s

### 9.5 当前开发状态

> **就绪矩阵**：见 `docs/需求文档/23-功能就绪矩阵.md` FR-PAY-06 → **待开发**
> **审查评级**：见 `docs/需求文档/21-PRD审查报告-Phase2&3.md` → **B+**（v1.1 已修复 3 项严重问题）
> **状态机说明**：消耗品扩展态 PAID→USED/EXPIRED/REFUNDED 见 01-§7.5 补充说明

---

## 十、变更记录

| 版本 | 日期 | 变更内容 |
|:---:|:---:|------|
| v1.0 | 2026-11-01 | 初版。3 个消耗品（超级喜欢 ¥0.99/个 / Boost ¥4.99/30min / 补充包 ¥0.99/20次），复用 VIP 支付通道，库存制管理 |
| v1.1 | 2026-11-02 | 明确表归 xfjy-pay-service（CONST-ARCH-004）；订单模型改为复用 tb_pay_order+biz_type=ITEM；补齐订单状态机 PENDING→PAID→USED/EXPIRED/REFUNDED；新增 Boost 叠加公式 |
