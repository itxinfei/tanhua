# 心飞交友 C 端 App 设计文档

> 适用范围：`xfjy-app/`（uni-app H5 + 微信小程序）  
> 生成日期：2026-11-01  
> 基线版本：v1.0（独立于后端微服务文档体系）

---

## 一、技术栈

### 1.1 框架与运行时

| 层 | 选型 | 版本 | 说明 |
|---|---|---|---|
| 前端框架 | Vue 3 | ^3.5.44 | Composition API + `<script setup lang="ts">` |
| 跨平台运行时 | uni-app | — | 一套代码编译为 H5 + 微信小程序 |
| 开发语言 | TypeScript | ^5.6.2 | 全量 TS，含 shime-uni.d.ts 全局类型声明 |
| 构建工具 | Vite + uni-plugin | — | `vite.config.ts`，Rollup 分包输出 |
| 状态管理 | Pinia | ^4.1.0 | 组合式 API（`defineStore` 箭头函数） |
| SSR | createSSRApp | — | `main.ts` 入口，阿里云函数计算 SSR 兜底 |

### 1.2 第三方库与 SDK

| 库 | 版本 | 用途 |
|---|---|---|
| uview-plus | ^3.8.130 | 核心 UI 组件库（对标 Element Plus 移动端版） |
| @dcloudio/uni-ui | ^1.5.12 | uni 官方基础组件（uni-icons / uni-popup / uni-load-more 等） |
| wukongimjssdk | 最新 | WuKongIM 长连接 SDK（WebSocket 消息收发） |
| easemob-websdk | ^5.1.3 | ⚠️ 已废弃（随 HuanXinController 删除），待 package.json 移除 |
| dayjs | ^1.11.23 | 时间格式化 |
| vue-i18n | ^11.4.16 | 国际化框架（当前仅中文，预留多语言） |

### 1.3 构建优化配置

```js
// vite.config.ts build 段
build: {
  rollupOptions: {
    output: {
      manualChunks: {
        "vendor-vue": ["vue", "vue-i18n", "pinia"],
        "vendor-ui": ["uview-plus"],
        "vendor-im": ["wukongimjssdk"],
      },
    },
  },
  minify: "esbuild",           // esbuild 压缩
  sourcemap: false,             // 生产无 sourcemap
  chunkSizeWarningLimit: 2048,  // 包体积告警阈值 2MB
}
```

---

## 二、工程结构

```
xfjy-app/
├── src/
│   ├── api/                  # 接口层（按业务域拆 TS 文件）
│   │   ├── auth.ts           # 登录/登出/注销/人脸识别
│   │   ├── chat.ts           # IM 凭证/联系人/通话/消息（含已废弃 getHuanXinUser）
│   │   ├── circle.ts         # 圈子动态发布/详情/评论/话题
│   │   ├── mine.ts           # 用户中心/VIP 购买/设置/账户
│   │   ├── match.ts          # 匹配/喜欢/超级喜欢
│   │   ├── kefu.ts           # 客服 IM 会话
│   │   ├── video.ts          # 小视频列表/详情/评论/上传
│   │   ├── agreement.ts      # 用户协议列表/详情
│   │   └── push.ts           # 推送 token 上报
│   ├── components/           # 全局组件（EasyCom 自动注册）
│   │   ├── a11y-button.vue        # 无障碍按钮（按钮 + 焦点环）
│   │   ├── a11y-dialog.vue        # 无障碍弹窗（Esc/焦点陷阱/语义化）
│   │   ├── a11y-radio-group.vue   # 无障碍单选组
│   │   ├── custom-tabbar.vue      # H5 自定义底栏（小程序走原生 tabBar）
│   │   ├── empty-state.vue        # 空状态占位
│   │   ├── match-popup.vue        # 匹配成功弹窗（"It's a Match!"）
│   │   ├── skeleton.vue           # 统一骨架屏（feed / video / match 三形态）
│   │   └── video-comment-drawer.vue  # 视频页评论抽屉
│   ├── pages/                # 业务页面（uni-app 路由 = 文件路径）
│   │   ├── auth/login.vue         # 登录（验证码 / 密码）
│   │   ├── match/
│   │   │   ├── index.vue          # 探花主页（全屏卡片堆叠 + 滑动匹配）
│   │   │   ├── detail.vue         # 对方资料详情（底部弹出抽屉）
│   │   │   ├── nearby.vue         # 附近的人（ES 地理排序列表）
│   │   │   └── guide.vue          # 完善资料引导页（首次登录强制跳转）
│   │   ├── circle/
│   │   │   ├── index.vue          # 圈子 feed 流（scroll-view + v-for）
│   │   │   ├── publish.vue        # 发布动态（图片 + 文字 + 话题）
│   │   │   ├── detail.vue         # 动态详情 + 评论区
│   │   │   └── tag.vue            # 话题聚合页
│   │   ├── video/
│   │   │   └── index.vue          # 小视频全屏瀑布流（±1 窗口节点缓存）
│   │   ├── chat/
│   │   │   ├── chat.vue           # 聊天会话页（WuKongIM 直连收发）
│   │   │   └── notifications.vue  # 系统通知（赞 / 评论 / 喜欢 / 公告）
│   │   ├── mine/
│   │   │   ├── index.vue          # 个人中心（用户信息 + VIP 状态 + 功能入口）
│   │   │   ├── profile.vue        # 编辑资料（头像 / 昵称 / 签名 / 照片墙）
│   │   │   ├── vip.vue            # VIP 会员购买页（套餐列表 + 支付）
│   │   │   ├── settings.vue       # 设置（推送 / 通知 / 缓存 / 关于我们）
│   │   │   ├── friends.vue        # 好友关系 / 新的朋友
│   │   │   ├── blacklist.vue      # 黑名单管理
│   │   │   ├── change-phone.vue   # 换绑手机号
│   │   │   ├── verify.vue         # 实名认证（待接口）
│   │   │   └── agreement.vue      # 用户协议 / 隐私政策查看
│   │   └── kefu/
│   │       ├── index.vue          # 在线客服工作台
│   │       ├── chat.vue           # 客服 IM 会话（与用户聊天隔离）
│   │       └── feedback.vue       # 意见反馈
│   ├── store/                # Pinia 状态管理
│   │   ├── user.ts           # 用户信息 / token / 登录态 / IM 凭证派生
│   │   ├── match.ts          # 匹配状态 / 匹配弹窗 / 滑动队列
│   │   ├── circle.ts         # 圈子 feed 数据 / 分页
│   │   └── chat.ts           # 联系人 / 消息未读数 / IM 凭证
│   ├── utils/                # 工具函数
│   │   ├── request.ts        # uni.request 封装（token / 401 重定向）
│   │   ├── auth-guard.ts     # 全局登录守卫（路由跳转前校验）
│   │   ├── wukongim.ts       # WuKongIM SDK 封装（login / logout / 事件监听）
│   │   ├── easemob.ts        # ⚠️ 已废弃（环信 SDK 封装，待删除）
│   │   ├── global-tabbar.ts  # H5 自定义底栏安装（非 tab 页注入）
│   │   ├── nav.ts            # 路由跳转封装（带 login guard）
│   │   ├── pay.ts            # 支付封装（微信 / 支付宝，TODO 待接入）
│   │   ├── push.ts           # 推送 token 上报
│   │   └── format.ts         # 时间 / 距离 / 数字格式化
│   ├── App.vue               # 应用入口（全局样式 + CSS 变量 + IM 登录 + 底栏）
│   ├── main.ts               # createSSRApp + Pinia + uview-plus
│   ├── pages.json            # 路由 + tabBar + EasyCom 配置
│   ├── theme.json            # 深色模式变量（导航栏 / 背景 / 文字）
│   ├── env.d.ts              # 环境变量类型声明
│   └── shime-uni.d.ts        // uni-app 全局类型补充
├── static/                   # 静态资源（tabbar 图标 / 品牌 logo / 启动图）
├── package.json              # 依赖清单
└── vite.config.ts            # Vite 构建配置
```

---

## 三、业务 UI 架构

### 3.1 导航体系

```
原生 tabBar（4 项 + H5 中间发布键）
┌────────┬────────┬──────┬────────┬────────┐
│ 匹配    │ 圈子    │  +   │ 视频    │ 我的    │
│ match  │ circle │ mid  │ video  │ mine   │
└────────┴────────┴──────┴────────┴────────┘

非 tab 页通过 installGlobalTabbar() 注入自定义底栏（H5 端）
小程序端走原生 tabBar，不支持的 midButton 用第 5 个 tab 项兜底（发布）
```

### 3.2 页面与业务模块映射

#### 模块 1：匹配（探花）—— 核心驱动

| 页面 | 路由 | 核心 UI | 数据源 |
|---|---|---|---|
| 探花主页 | `match/index` | 全屏卡片堆叠（Tinder 式滑动）、底部操作栏（返回 / 喜欢 / 超喜 / 踩） | xfjy-server `GET /users/recommend` |
| 资料详情 | `match/detail` | 底部弹出抽屉（照片墙 / 基本信息 / 标签 / 共同好友） | xfjy-server `GET /users/{id}` |
| 附近的人 | `match/nearby` | 列表（头像 / 昵称 / 距离 / 签名），支持下拉刷新 | xfjy-elasticsearch `POST /api/es-user-location/nearby` |
| 完善资料 | `match/guide` | 强制引导（头像 / 昵称 / 兴趣标签 / 照片墙），首次登录拦截 | xfjy-user-service `PUT /api/users/me` |

**交互设计**：
- 左右滑动卡片 → 触发动画 + 接口上报
- 双向喜欢 → `match-popup.vue` 弹窗（"It's a Match!" + 心跳动画）
- 顶部操作栏按钮缩放反馈（`btn-feedback` class）
- 使用 `requestAnimationFrame` 节流拖拽手势

#### 模块 2：圈子 —— 动态社交

| 页面 | 路由 | 核心 UI | 数据源 |
|---|---|---|---|
| feed 流 | `circle/index` | scroll-view 信息流（头像+昵称+正文+图片网格+互动栏） | xfjy-server `/movements` |
| 发布动态 | `circle/publish` | 图片选择器（最多 9 张）+ 文字输入 + 话题标签选择 | xfjy-server `POST /movements` |
| 动态详情 | `circle/detail` | 全文展示 + 评论树（支持二级回复）+ 底部互动栏 | xfjy-server `GET /movements/{id}` |
| 话题聚合 | `circle/tag` | 话题卡片列表 + feed 二级流 | xfjy-server `GET /movements/tag/{tagId}` |

**交互设计**：
- 下拉刷新 + 上拉加载更多（`lower-threshold`）
- 图片懒加载（`lazy-load`）
- 双击点赞（心形飘起动画 `heart-beat`）
- 评论抽屉从底部滑出（`video-comment-drawer` 组件复用）
- ⚠️ **性能风险**：当前 scroll-view + v-for 全量 DOM 渲染，>100 条时卡顿；建议引入虚拟列表

#### 模块 3：视频 —— 类抖音

| 页面 | 路由 | 核心 UI | 数据源 |
|---|---|---|---|
| 视频流 | `video/index` | 全屏上下滑动切换（抖音式）、右侧互动栏（头像 / 喜欢 / 评论 / 分享）、底部歌名跑马灯 | xfjy-server `GET /small-videos` |

**交互设计**：
- ±1 窗口：当前屏 + 上下各 1 屏的视频节点常驻（减少白屏）
- 切换时封面图优先加载，视频第二帧解码后置换为 video 播放
- 进度条 / 音量跟随系统
- 评论抽屉（`video-comment-drawer` 组件）

#### 模块 4：即时通讯

| 页面 | 路由 | 核心 UI | 数据源 |
|---|---|---|---|
| 聊天会话 | `chat/chat` | 消息气泡列表（左收 / 右发）+ 底部输入栏（文字 / 图片 / 语音）+ 顶部对方昵称 | WuKongIM 直连（`wukongim.ts` 封装） |
| 通知中心 | `chat/notifications` | 分类 Tab（赞 / 评论 / 喜欢 / 系统公告）| xfjy-server `GET /messages/**` |

**交互设计**：
- WuKongIM 长连接由 `App.vue onShow` 幂等登录（`loginIMDirect`）
- 断线自动重连 + 重连令牌刷新
- 未读数角标（`pulse` 动画提醒）
- 图片 / 语音消息气泡独立组件，支持重发 + 读屏语义

#### 模块 5：我的 —— 用户中心

| 页面 | 路由 | 核心 UI | 数据源 |
|---|---|---|---|
| 个人中心 | `mine/index` | 顶部用户卡片（头像 / 昵称 / VIP 徽章）+ 功能区网格（好友 / 会员 / 设置 / 客服）| xfjy-server `GET /users/me` |
| 编辑资料 | `mine/profile` | 头像上传 + 昵称 + 签名 + 性别 + 年龄 + 照片墙管理 | xfjy-user-service `PUT /api/users/me` |
| VIP 购买 | `mine/vip` | 套餐卡片列表（月 / 季 / 年）+ 底部支付条（微信 / 支付宝）| xfjy-server `POST /vip/orders` |
| 设置 | `mine/settings` | 列表分组（推送 / 通知 / 缓存清理 / 关于我们 / 退出登录）| 仅前端操作 |
| 好友关系 | `mine/friends` | 好友列表 + 新的朋友（待通过）+ 推荐好友 | xfjy-server `GET /messages/contacts` |
| 黑名单 | `mine/blacklist` | 被封禁用户列表（支持解除）| xfjy-server `GET /users/blacklist` |
| 换绑手机 | `mine/change-phone` | 旧号验证码 → 新号验证码 → 绑定成功 | xfjy-server `POST /users/change-phone` |
| 实名认证 | `mine/verify` | 姓名 + 身份证号 + 人脸识别（TODO）| 百度人脸（TODO） |
| 协议查看 | `mine/agreement` | WebView 渲染 Markdown 协议内容 | xfjy-server `GET /agreements/{type}` |

#### 模块 6：在线客服

| 页面 | 路由 | 核心 UI | 数据源 |
|---|---|---|---|
| 客服工作台 | `kefu/index` | 问题分类 FAQ + 快捷入口（在线咨询 / 意见反馈）| xfjy-server `GET /faqs` |
| 客服聊天 | `kefu/chat` | 横向复用 chat.vue 的消息气泡 UI（独立 WuKongIM agent 账号）| WuKongIM agent 会话 |
| 意见反馈 | `kefu/feedback` | 反馈类型 + 描述 + 图片上传 + 联系方式 | xfjy-server `POST /feedback` |

---

## 四、设计系统（Design Tokens）

### 4.1 品牌色

| Token | 亮色模式 | 深色模式 | 用途 |
|---|---|---|---|
| `--color-primary` | `#ff5e9c` 玫红 | `#ff8eb9` | 主操作按钮 / 图标高亮 |
| `--color-primary-deep` | `#b13bff` 深紫 | `#9b5cf6` | 渐变起点 / 超喜按钮 |
| `--color-primary-violet` | `#8a5cf6` 紫罗兰 | `#a78bfa` | 辅助装饰 |
| `--color-primary-soft` | `#ff8fb1` 浅粉 | `#ff8fb1` | 标签底色 / 徽标 |
| `--color-primary-mist` | `#ffd1e8` 薄雾 | `#7a5a70` | 背景浅色块 |
| `--color-primary-text` | `#c2185b` 深玫 | `#ff8eb9` | 强调文字 |

### 4.2 渐变

| Token | 取值 | 用途 |
|---|---|---|
| `--gradient-brand` | `linear-gradient(135deg, #b13bff, #ff5e9c)` | 主按钮 / 标题装饰 |
| `--gradient-brand-h` | `linear-gradient(90deg, #b13bff, #ff5e9c)` | 渐变文字 / 弹窗标题 |
| `.card-mask` | `linear-gradient(to top, rgba(0,0,0,0.75), transparent)` | 卡片底部遮罩 |

### 4.3 字体与间距

| 维度 | 规定 |
|---|---|
| 全局字号 | `28rpx`（基准）|
| 大标题 | `64rpx`（匹配弹窗）/ `48rpx`（页面标题）|
| 正文 | `28rpx` |
| 辅助文字 | `24rpx` |
| 圆角 | `12rpx`（卡片）/ `24rpx`（全园按钮）/ `999rpx`（胶囊）|
| 安全区 | `env(safe-area-inset-bottom)` 适配 iPhone 刘海 |

### 4.4 动效

| Token | 动画 | 时长 | 用途 |
|---|---|---|---|
| `.pulse` | `scale 1→1.15→1` 循环 | 1.5s | 徽标提醒 |
| `.pop-in` | `scale 0.6→1.05→1` 弹出 | 0.35s | 弹窗出现 |
| `.heart-beat` | `scale 1→1.3→1.1→1.25→1` 弹跳 | 0.6s | 心形飘起 |
| `.float` | `translateY(0→-20rpx→0)` 漂浮 | 3s | 装饰性浮动 |
| `.btn-feedback` | `transform + opacity` 按压缩放 | 0.15s | 按钮点击反馈 |
| `.btn-gradient` | `transform + box-shadow` 按压 | 0.15s | 渐变主按钮 |
| `.skeleton`（shimmer） | `background-position` 扫描 | 1.4s | 骨架屏闪烁 |

> 所有装饰性动画通过 `@media (prefers-reduced-motion: reduce)` 统一关闭，满足 WCAG 2.3.3 / 2.2.2

---

## 五、无障碍设计（WCAG 2.1 AA）

| 类别 | 规范 |
|---|---|
| 颜色对比度 | 辅助文字由 `#999` 提深至 `#767676`（4.54:1），正文 `#333` 在白色上 12.6:1 |
| 焦点可见 | `:focus-visible` 统一 2px 品牌色描边 + 2px offset（WCAG 2.4.7）|
| 语义化弹窗 | `a11y-dialog.vue` 支持 Esc 关闭 + 焦点陷阱 + ARIA role="dialog" |
| 读屏适配 | 装饰性图标 `aria-hidden="true"`；头像 `aria-label="xxx 的头像"` |
| 动效敏感 | `prefers-reduced-motion: reduce` 关闭所有循环动画 |
| 强制颜色 | `forced-colors: active` 回退 `.gradient-text` 为系统色（CanvasText）|
| 触控区域 | 所有可点击元素 ≥ 44px（uview-plus 默认满足）|

---

## 六、状态管理架构

```
Pinia（组合式 API）
├── userStore          # 用户信息 / token / IM 凭证派生 / 登录态
│   ├── state: userInfo, token, imToken
│   ├── action: login(), signOutAll(), refreshUserProfile()
│   └── getter: isLoggedIn, isVip
├── matchStore         # 匹配队列 / 匹配弹窗 / 滑动状态
│   ├── state: cardQueue[], matchPopup, likeHistory[]
│   ├── action: nextCard(), like(), superLike(), dislike()
│   └── getter: hasMatchPopup, canSwipe
├── circleStore        # 圈子 feed / 分页
│   ├── state: feedList[], page, hasMore
│   ├── action: loadFeed(), prependItem(), appendPage(), deduplicate()
│   └── getter: isEmpty, total
└── chatStore          # 联系人 / 未读数 / IM 会话
    ├── state: huanXinUser, contactList[], unreadCount
    ├── action: loadContacts(), addContact(), loadHuanXinUser()
    └── getter: totalUnread
```

---

## 七、网络与数据层

### 7.1 请求封装

| 功能 | 实现 |
|---|---|
| 基类 | `uni.request` 封装于 `utils/request.ts` |
| 认证 | 请求头 `Authorization: Bearer ${token}` |
| 401 处理 | 并发去重（`redirectingToLogin` 标志）+ `uni.reLaunch` 跳登录 |
| 避免循环依赖 | `userStore` 懒加载（`import()` 在拦截器内部）|

### 7.2 全局守卫

- `auth-guard.ts`：路由跳转前校验 `token`；未登录 + 目标页非公开 → `reLaunch` 到登录页
- 公开页面白名单：`login` / `agreement` / 静态页

### 7.3 WuKongIM 直连

```
App.vue onShow
  └─ loginIMDirect(uid)  →  本地派生 token `xfjy_${uid}_im_token`
                            →  wukongimjssdk.login(uid, token)
                            →  连接 WebSocket wss://192.168.20.133:5200
                            →  状态刷新（isLoggedIn = true）
```

- 中间发布键 → `pages/circle/publish`（仅微信小程序走 `onTabBarMidButtonTap`，H5 走 CSS 绘制"+"）
- 会话页 chat.vue / kefu/chat.vue 共享同款消息气泡 UI

---

## 八、多端适配

| 维度 | H5 | 微信小程序 |
|---|---|---|
| 导航栏 | 自定义样式 + CSS 变量主题 | 原生导航栏（theme.json 控制）|
| 底栏 | 自定义绘制（`uni-tabbar__mid` 纯 CSS "+"） | 原生 tabBar + midButton API |
| 深色模式 | `@media (prefers-color-scheme: dark)` + CSS 变量 | `theme.json` + 系统适配 |
| IM 连接 | `wss://192.168.20.133:5200` | `wss://192.168.20.133:5200` |
| 支付 | 微信 H5 支付 / 支付宝网页支付 | `wx.requestPayment` 原生 |
| 推送 | 浏览器通知权限（待） | 微信订阅消息 |

---

## 九、性能设计

| 维度 | 实现 | 状态 |
|---|---|---|
| 代码分包 | vendor-vue / vendor-ui / vendor-im 三 chunk | ✅ |
| 首屏加载 | esbuild 压缩 + sourcemap 关闭 + 分 chunk 并行加载 | ✅ |
| 图片优化 | 懒加载（`lazy-load`）+ `mode="aspectFill"` | ✅ |
| 视频缓存 | ±1 窗口预加载 / 当前屏视频优先 + 两侧封面占位 | ✅ |
| 列表性能 | circle feed ⚠️ scroll-view + v-for 全量 DOM（建议虚拟列表）| ⚠️ 待优化 |
| 动画性能 | requestAnimationFrame 节流；复杂动画 use CSS `transform` | ✅ |
| 骨架屏 | 全局 `skeleton.vue`（feed / video / match 三形态）+ shimmer 动画 | ✅ |
| 内存管理 | 离开页面时销毁 WuKongIM 监听器；图片离屏释放 | 部分 |

---

## 十、标注与待办

### 10.1 已废弃待清理

| 项 | 位置 | 替代 | 风险 |
|---|---|---|---|
| `easemob-websdk` | `package.json` | `wukongimjssdk` | +150KB 死代码 |
| `getHuanXinUser()` | `api/chat.ts` | `loginIMDirect()` | 已无调用方 |
| `loadHuanXinUser()` 缓存 | `store/chat.ts` | IM 凭证由 App.vue onShow 派生 | 静态 state 残留 |
| 环信注释（5 处）| `store/chat.ts` / `user.ts` / `App.vue` | WuKongIM | 文档误导 |

### 10.2 已知性能瓶颈

| 项 | 影响 | 建议 |
|---|---|---|
| circle feed 全量 DOM | 动态数 > 100 时滚动掉帧 | 引入 `uni-recycle-list` 虚拟列表 |
| video 节点复用 | 当前复用视频节点池小，大流量场景白屏 | 引入预加载队列 + 三级缓存（封面 / 元数据 / 视频流）|
| uview-plus 全量引入 | 未按需 import，首屏加载全组件样式 | 按需引入或 tree-shake 未使用组件 |

### 10.3 功能缺口（FR-FURTURE 映射）

| FR-ID | 缺口 | 影响页面 |
|---|---|---|
| FR-FURTURE-37/39 | 个人主页公开 URL / 分享裂变 | `mine/profile.vue` |
| FR-FURTURE-38 | VIP 订单记录页 | `mine/vip.vue` |
| FR-FURTURE-35 | 短视频发布入口完整 | `video/index.vue` |

---

## 十一、变更记录

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1.0 | 2026-11-01 | 初版。覆盖技术栈、业务 UI、设计系统、组件体系、状态管理、网络层、多端适配、性能设计、无障碍 |
