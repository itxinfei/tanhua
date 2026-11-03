# PRD：新用户兴趣选择与引导（Phase 1 P0）

> FR-ID: **FR-USER**（新用户兴趣选择，归入 FR-USER 系列——与资料/画像合并管理）
> 优先级：**P0**  
> 负责人：前端 1 人 + 后端 1 人  
> 预估工时：前端 2d + 后端 1.5d + 联调测试 0.5d = 4d  
> 依赖项：无  
> FR-ID（需求文档关联）：FR-AUTH-01（登录）之后

---

## 一、背景与目标

### 1.1 业务背景

新用户注册后直接进入匹配页，系统仅依靠位置 + 基础画像做冷启动推荐，缺乏**兴趣维度**导致：
- 前 3 天配对成功率仅 2-3%（竞品 5-8%）
- 用户首轮体验无明显"被理解"感，次日留存仅 25%

### 1.2 数据目标

| 指标 | 当前 | 目标 |
|------|:---:|:---:|
| 新用户配对成功率（首 3 天）| 2-3% | 6%+ |
| 新用户次日留存 | 25% | 35%+ |
| 新用户首周资料完善率 | 30% | 70%+ |

### 1.3 非目标

- 不在本版本实现多模态兴趣模型（上传照片识别兴趣）
- 不在本版本引入社交链（"你朋友也喜欢 XXX"）
- 不强制兴趣数量（用户可跳过）

---

## 二、功能描述

### 2.1 用户流程

```
登录成功
  │
  ├─ 已有兴趣记录 → 正常进入匹配页（不变）
  │
  └─ 无兴趣记录（firstLogin === true / interestCount === 0）
       │
       ▼
  兴趣选择页（强制访问，完成后才能进匹配页）
       │
       ├─ 选 ≥ 3 个兴趣 → POST /users/interests → switchTab('/pages/match/index')
       │
       └─ 跳过（最多允许跳过 1 次）→ 保留 14 天倒计时提醒补选 → switchTab
```

### 2.2 兴趣标签设计

#### 标签体系（一期固定 36 个，6 分类 × 6 标签）

| 分类 | 英文key | 标签 |
|------|---------|------|
| 📸 影音娱乐 | media | 电影/音乐/摄影/动漫/综艺/游戏 |
| 🏃 运动健身 | sport | 跑步/健身/游泳/瑜伽/骑行/登山 |
| 🎨 艺术生活 | art | 绘画/书法/手工/花艺/穿搭/做饭 |
| ✈️ 出行探索 | travel | 自驾游/背包客/露营/探店/宠物/自然 |
| 📖 自我提升 | growth | 阅读/写作/学习/演讲/考证/创业 |
| 🎯 社交互动 | social | 剧本杀/密室/桌游/品酒/钓鱼/K歌 |

#### 数据结构变更

```sql
-- 新增表 tb_user_interest
CREATE TABLE tb_user_interest (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL COMMENT '用户ID',
    tag_key     VARCHAR(32) NOT NULL COMMENT '标签key(如:travel.visit)',
    tag_name    VARCHAR(64) NOT NULL COMMENT '标签中文名',
    weight      TINYINT DEFAULT 5 COMMENT '权重(1-10,默认5)',
    created     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user_id(user_id),
    UNIQUE uk_user_tag(user_id, tag_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户兴趣标签';
```

### 2.3 页面设计

#### 兴趣选择页（pages/auth/interest-select.vue）

**顶部**
```
"完善您的兴趣"（大标题）
"至少选择 3 个，帮助我们为您推荐更合适的人"（副标题，灰色小字）
[跳过]（右上角 Text Button，小字）
```

**中部**
```
🖼 影音娱乐
┌────┐ ┌────┐ ┌────┐
│ 电影 │ │ 音乐 │ │ 摄影 │   ← 每行 3 个，圆角标签
└────┘ └────┘ └────┘
┌────┐ ┌────┐ ┌────┐
│ 动漫 │ │ 综艺 │ │ 游戏 │
└────┘ └────┘ └────┘

🏃 运动健身
...（同上）

（向下滚动加载剩余 5 个分类）
```

**底部**
```
┌──────────────────────────────────────────┐
│  已选 2 项（最少选 3 项）                    │
│  [──────────────────────] 进度条            │
│                                          │
│         [保存并继续]（选中≥3个时高亮）        │
└──────────────────────────────────────────┘
```

#### 交互细节

| 操作 | 反馈 |
|------|------|
| 点击未选标签 | 标签变为选中状态（主题色边框 + 填充 + 右上角 ✓），底部计数"已选 N 项" +1 |
| 点击已选标签 | 取消选中，计数 -1 |
| 达到 3 个选中 | 进度条满，按钮变为高亮可点击状态，按钮文案"保存并继续" |
| 不足 3 个点"保存" | 按钮 disabled（灰色），hover 提示"请至少选择 3 个" |
| 点击"跳过" | toast"你可以在"我的-兴趣标签"中随时补充" → switchTab match |
| 保存中 | 按钮显示 loading spinner + "保存中..." |

---

## 三、接口设计

### 3.1 保存兴趣标签

- **接口**：`POST /users/interests`
- **描述**：保存或更新用户兴趣标签（全量覆盖）
- **认证**：需要 Token
- **请求体**：

```json
{
  "interests": [
    { "tagKey": "media.movie", "tagName": "电影", "weight": 8 },
    { "tagKey": "sport.running", "tagName": "跑步", "weight": 5 },
    { "tagKey": "travel.camping", "tagName": "露营", "weight": 7 }
  ]
}
```

- **响应**（`Result<Void>`）：`{code:200, message:"保存成功", data:null}`

### 3.3 接口错误响应

| 接口 | HTTP | 错误码 | 场景 | message |
|------|:---:|:---:|------|---------|
| `POST /users/interests` | 400 | `INVALID_PARAM` | interests 为空或格式错误 | "兴趣数据不合法" |
| `POST /users/interests` | 401 | `AUTH_FAILED` | Token 过期或未登录 | "请先登录" |
| `POST /users/interests` | 409 | `ALREADY_SET` | 用户已有兴趣且 force=false | "兴趣已设置，如需修改请使用更新接口" |
| `POST /users/interests` | 413 | `INTEREST_LIMIT` | interests 数量超过 20 个 | "兴趣标签最多选择 20 个" |
| `GET /users/interests` | 401 | `AUTH_FAILED` | Token 过期或未登录 | "请先登录" |
| `GET /users/interest-status` | 401 | `AUTH_FAILED` | Token 过期或未登录 | "请先登录" |

> 前端统一处理：401 → 跳转登录；400/413 → 弹窗提示后端返回的 message；409 → 视为已设置，直接进入匹配页。

### 3.4 依赖项与调用链

- **前置依赖**：FR-AUTH-01（登录认证）→ 本 PRD 在登录后触发
- **上游调用方**：无
- **下游被依赖方**：FR-REC-02（探花匹配，使用兴趣标签加权）/ FR-ENGINE-01（行为评分采集）
- **数据流**：`interest-select.vue` → `POST /users/interests` → `tb_user_interest` → 推荐引擎读取

---

## 四、推荐算法侧改动

### 4.1 评分权重调整

在现有混合评分公式（PyTorch 双塔 + 距离衰减 + 画像相似度）中新增：

```
finalScore = w1 * vectorScore
           + w2 * distanceScore
           + w3 * profileScore
           + w4 * interestScore   ← 新增

其中 interestScore = commonInterestCount / maxInterestCount * 100
```

一期固定 `w4 = 0.1`（可配置 Nacos 参数 `xfjy.recommend.weight.interest`）。

### 4.2 实现方式

- 查询用户兴趣 → 解析成 `Set<String>`
- 与候选用户兴趣取交集 `commonTags`
- 按交集数量归一化得分（0-100）
- 空兴趣用户不参与此项评分（interestScore = 0，不影响其他维度）

---

## 五、边界场景

| 场景 | 处理 |
|------|------|
| 用户跳过兴趣引导 | 允许进入匹配页，14 天后在「我的 - 兴趣标签」入口展示 |
| 网络失败保存 | ux.fail("保存失败，请重试") + 本地暂存选中状态 |
| 极速连点"保存" | 按钮 500ms debounce，保存中 disabled |
| 零兴趣用户调用推荐 | interestScore 不参与计算，其他 3 个权重不变 |
| 兴趣标签库更新 | 后端配置，前端启动时通过接口拉取标签全集 |

---

## 六、测试验收

### 6.1 前端测试

| # | Given | When | Then | 验收方式 |
|---|-------|------|------|---------|
| T1 | 注册成功，interestCount=0 | 进入 App | 自动跳转兴趣选择页（而非匹配页）| 接口 mock |
| T2 | 兴趣页首次加载 | 点击 2 个标签 | 按钮灰色 disabled，显示"已选 2 项" | 真机/模拟器 |
| T3 | 兴趣页 | 点击第 3 个标签 | 按钮高亮，显示"已选 3 项" | 真机/模拟器 |
| T4 | 选中 3 个 | 点"保存并继续" | loading... → toast"保存成功" → 跳匹配页 | 真机 |
| T5 | 兴趣页 | 点"跳过" | 弹确认 → 跳匹配页 → 14 天后才出现入口 | 真机 |
| T6 | 已选完后再次进入 | 点"保存并继续" | 调 POST /users/interests 接口（幂等）| 抓包 |

### 6.2 后端测试

| # | Given | When | Then |
|---|-------|------|------|
| T7 | 无兴趣用户 | POST 5 个兴趣 | tb_user_interest 插入 5 条，返回 200 |
| T8 | 已有 3 个兴趣 | POST 2 个（tagKey 不同）| 删除旧 3 条 + 插入新 2 条（全量覆盖）|
| T9 | 兴趣数=0 的用户调推荐 | GET /match/cards | interestScore=0，其余维度得分正常 |

### 6.3 埋点事件

> 事件命名以 07-PRD-Phase1-基础埋点体系.md §3.1 为准。`interest_save_click` / `interest_save_success` 已合并归入标准事件 `interest_save`。

| 事件名 | 触发时机 | 属性 |
|--------|---------|------|
| `interest_page_view` | 兴趣页曝光 | category_count, source |
| `interest_save` | 兴趣保存完成（点击"开始匹配"后）| interest_count |
| `interest_skip` | 用户点击"跳过" | — |

---

## 七、UI 设计规范与功能边界

> 本节为样式与功能约束，开发时**必须遵守**，禁止自行发挥视觉风格或超出边界实现。

### 7.1 设计系统引用（强制）

| 规范层 | 来源 | 关键约束 |
|--------|------|---------|
| 品牌色 | `docs/AI-agent交接-品牌主色切换.md` | 选中标签用主色 `#fe3c72` 描边+浅底 `#ffeff5`；底部按钮主渐变；**禁止硬编码色值、禁用旧色 `#ff5e9c`** |
| 图标 | `docs/icon-icons.md` | 兴趣分类图标用 Phosphor（`i-ph-camera` / `i-ph-music` 等）；**禁止 emoji 充当分类图标** |
| 组件 | `docs/需求文档/03-开发规范.md` §3 | 标签复用 chip 组件；按钮复用 `a11y-button`；圆角走 SCSS 变量 |
| 暗色模式 | `docs/需求文档/03-开发规范.md` | 强制支持；选中态浅底暗色用 `#3a1a2a` |

### 7.2 页面级 UI 规格

| 元素 | 规格 |
|------|------|
| 兴趣标签（未选中） | 高 72rpx，圆角 `$uni-border-radius-pill(999rpx)`，边框 2rpx `--color-border`，文字 `--color-text-primary` |
| 兴趣标签（选中） | 边框主色 `$uni-color-primary`，浅底 `#ffeff5`（暗色 `#3a1a2a`），文字 `#c2185b` |
| 标签网格 | flex-wrap，间距 16rpx，每行自适应 3-4 个 |
| 底部按钮区 | 固定底部，"跳过"ghost 按钮 + "完成（N/3）"primary 按钮并排 |
| 完成按钮 | `a11y-button` type="primary"，高 88rpx，选满 3 个后激活主渐变，未满时 disabled 灰 |

### 7.3 必须实现

- [ ] 兴趣选择页：标签网格 + 选中态 + 计数提示（≥3 可完成）
- [ ] "跳过"按钮始终可用（不强制选择）
- [ ] 完成后 `switchTab('/pages/match/index')` 进入匹配页
- [ ] 选中/未选中态色值走 CSS 变量，暗色模式适配
- [ ] 兴趣分类图标用 Phosphor `i-ph-*`

### 7.4 禁止实现

- [ ] 禁止强制选择（必须可跳过）
- [ ] 禁止 emoji 充当兴趣分类图标
- [ ] 禁止硬编码品牌色值
- [ ] 禁止在引导页插入广告/付费墙
- [ ] 禁止超过 2 级引导步骤（选择兴趣 → 进匹配页，一步完成）

### 7.5 当前开发状态

> **就绪矩阵**：见 `docs/需求文档/23-功能就绪矩阵.md` FR-USER（兴趣选择）→ **待开发**
> **审查评级**：Phase 1 PRD 不在 21 号评级范围

---

## 八、变更记录

| 版本 | 日期 | 变更内容 |
|:---:|:---:|------|
| v1.0 | 2026-11-01 | 初版。兴趣 6 分类 × 6 标签，36 个固定标签，后端全量覆盖保存逻辑，推荐加权 w4=0.1 |
