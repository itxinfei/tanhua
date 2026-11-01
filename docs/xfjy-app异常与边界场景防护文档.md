# 心飞交友 C 端 App 异常与边界场景体验防护文档

> 适用范围：`xfjy-app/`（uni-app H5 + 微信小程序）  
> 审计视角：**用户的"倒霉时刻"**——所有可能让 App 表现异常的场景  
> 生成日期：2026-11-01  
> 基线版本：v1.0

---

## 〇、核心理念

**"用户不关心你为什么出错，只关心现在怎么办。"**

每一条异常提示都应遵循 **3S 原则**：

| 原则 | 说明 | 反例 ❌ | 正例 ✅ |
|---|---|---|---|
| **Short** | 一句话说清楚 | "Request failed with status code 500 Internal Server Error" | "服务器繁忙，请稍后再试" |
| **Specific** | 告诉用户**什么**出了问题 | "发生错误" | "图片上传失败，文件过大" |
| **Solution** | 给出**下一步**操作 | "操作失败" | "网络不稳定，请点击重试" |

---

## 一、网络异常场景

### 1.1 断网（无网络连接）

#### 触发条件
- 用户开启飞行模式
- WiFi 和移动数据都关闭
- 欠费停机
- 路由器故障但手机仍连 WiFi

#### 用户感知链路

```
任何请求发出 → uni.request fail（"request:fail 网络连接失败"）
           → 当前 catch 分支仅 console.error（H5）/ 小程序静默
           → 用户看到：页面白屏 / 按钮点击无反馈 / loading 转圈不止
```

#### 防护设计

| 层级 | 实现 | 用户感知 |
|---|---|---|
| **全局网络监听** | `uni.onNetworkStatusChange` | 断网时**顶部黄色提示条** + 阻止核心交互 |
| **请求前校验** | `uni.getNetworkType` 如为 `none` | toast"无网络连接" + 不发请求，避免用户苦等超时 |
| **请求失败兜底** | catch 中区分 fail 类型 | 区分性文案（"网络不稳定" / "无法连接服务器"）|
| **恢复自动重连** | 网络恢复时 (`networkType !== 'none'`) | 提示条变绿"网络已恢复" + 重试最近失败请求 |

#### 提示条 UI 规范

```
┌────────────────────────────────────────┐
│ 🟡 网络连接已断开，请检查网络设置        │  ← 黄色 #FFF3CD 背景
└────────────────────────────────────────┘
┌────────────────────────────────────────┐
│ 🟢 网络已恢复                            │ → 3s 后自动消失
└────────────────────────────────────────┘
```

#### 已登录用户断网时的行为

| 页面 | 允许行为 | 阻止行为 |
|---|---|---|
| 匹配页 | 浏览已加载的缓存卡片 | 新卡片请求 / 喜欢 / 滑动上报 |
| 聊天页 | 查看已加载的消息历史 | 发送消息 / 接收新消息 |
| 圈子页 | 浏览已加载的 feed | 发布 / 评论 / 加载更多 |
| 视频页 | 观看已缓冲的视频 | 切换下一个 |

#### 代码实现要点

```ts
// utils/network-monitor.ts（新建）
export function startNetworkMonitor(): void {
  // 启动时立即检查一次
  uni.getNetworkType({
    success: (res) => {
      isOnline.value = res.networkType !== 'none';
    },
  });
  // 持续监听
  uni.onNetworkStatusChange((res) => {
    const wasOffline = !isOnline.value;
    isOnline.value = res.isConnected;
    if (wasOffline && isOnline.value) {
      emit('network-restored');
      ux.info('网络已恢复');
      hideNetworkBar();
    } else if (!isOnline.value) {
      showNetworkBar();
    }
  });
}
```

---

### 1.2 弱网 / 高延迟

#### 触发条件
- 信号边缘（1~2 格）
- 地铁 / 电梯 / 地下室
- 带宽拥塞（商场 / 演唱会）
- 跨国高延迟（海归用户）

#### 超时策略

| 接口类型 | 超时阈值 | 说明 |
|---|---|---|
| 关键请求（登录、支付） | 10s | 资金敏感 |
| 普通 API | 8s | 标准 |
| 文件上传 | 30s | 大文件容忍 |
| 图片加载 | 15s | CDN 下载 |
| IM WebSocket 心跳 | 5s+3 次重试 | WuKongIM 自带 |

#### 弱网体感优化

| 场景 | 当前 | 建议 |
|---|---|---|
| 按钮点击后 | 无任何反馈，用户反复点击 | **即时 loading 态**（按钮 disable + spinner）|
| 列表滚动加载 | 用户拉到空白无提示 | **底部"加载中…"** + 超时后"加载失败，点击重试" |
| 图片加载 | 空白占位 | **渐进加载**（先低清缩略图→高清），Shimmer 占位 |
| 滑动卡片 | 滑走后无响应 | **乐观 UI**：卡片立即移除 + 后端失败时 toast + 卡片回退 |

#### 请求级超时处理

```ts
// utils/request.ts 改造
const TIMEOUT_MS = 8000;

// uni.request 本身不支持 timeout 字段（H5 支持），用 AbortController 模拟
function requestWithTimeout(opts): Promise<any> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      reject({ errMsg: '请求超时，网络环境较差' });
    }, TIMEOUT_MS);

    uni.request({
      ...opts,
      success: (res) => { clearTimeout(timer); resolve(res); },
      fail: (err) => { clearTimeout(timer); reject(err); },
    });
  });
}
```

---

### 1.3 DNS 解析失败

#### 触发条件
- 污染 DNS
- 域名到期
- 运营商劫持

#### 用户提示

```
标题："无法连接到服务器"
正文："请检查您的网络设置，稍后重试"
[重试按钮]  [反馈问题 → 复制错误码到剪贴板]
```

#### 兜底策略
- 错误码 `REQUEST_DNS_FAIL` → 不区分 H5 / 小程序，统一文案
- 提供"反馈问题"入口 → 附带 `traceId`（后端 trace header）便于排查

---

## 二、服务端异常场景

### 2.1 500 内部错误

#### 当前问题
后端可能直接抛堆栈（Spring Boot 默认 error page）→ HTML 直接透传到前端

#### 防护设计

| 层级 | 处理 |
|---|---|
| **网关层** | `GlobalExceptionHandler` 兜底返回 `{code:500, message:"服务器繁忙"}` |
| **前端拦截** | response `code !== 200` 且 `code !== 201` → ux.fail(message) |
| **堆栈透传拦截** | response header `Content-Type: text/html` → 说明是网关级错误，显示"服务暂不可用"通用页 |
| **敏感信息屏蔽** | 生产环境不暴露 `errMsg` 中的堆栈 / SQL / IP |

#### 严重度过级

| 错误类型 | 前端行为 |
|---|---|
| 500 业务级（message 可展示） | toast(message) |
| 500 系统级（message 是 HTML / null） | 全屏错误页 + "服务器繁忙" + 重试 |
| 502 Bad Gateway | "服务正在恢复，请稍候" |
| 503 Service Unavailable | 维护页（可配静态 HTML："心飞交友将于 XX:XX 恢复服务"）|

---

### 2.2 401 登录态失效

#### 触发条件
- token 过期（默认 7 天 / 30 天）
- 用户在另一台设备登录
- 管理员强制下线

#### 当前问题
`request.ts` 已有 401 处理（清 token + `reLaunch` 登录页），但：
1. **在途多请求同时 401**：虽有 `redirectingToLogin` 去重，但其他请求的 Promise rejection 未兜底
2. **从后台回前台 token 过期**：用户之前在聊天页，回来后断开 IM，但页面仍显示在线用户发的历史消息——新消息收发受影响但无提示
3. **微信 / 小程序 session 过期**：需重新 `login()` 获取 code → 需兼容

#### 防护设计

```
401 响应
  ├─ redirectingToLogin = false
  │   ├─ 清 token + 清 userStore
  │   ├─ 发全局事件 'auth:expired'
  │   ├─ 所有订阅者（chatStore / matchStore）清理状态
  │   ├─ showModal("登录已过期，请重新登录")
  │   └─ 用户确认后 reLaunch 登录页
  │
  └─ redirectingToLogin = true → 静默丢弃后续 401，避免重复弹窗
```

#### 各页面在 401 时的行为

| 页面 | 行为 |
|---|---|
| 匹配页 | 保留已加载卡片但不允许互动 → 恢复登录后自动上报未完成的滑动 |
| 聊天页 | 保留历史消息但发送区 disabled → 恢复登录后自动重连 IM |
| 圈子页 | 保留已加载 feed → 恢复登录后允许继续操作 |
| 视频页 | 允许继续观看已缓冲视频 → 切换新视频时拦截 |

---

### 2.3 403 权限不足

#### 触发场景
- 普通用户尝试访问管理员接口
- VIP 专属功能对非 VIP 请求
- 被封禁用户发起操作

#### 防护设计

| 子场景 | 前端行为 |
|---|---|
| VIP 功能拦截 | toast"这是 VIP 专属功能" → [开通按钮] 跳转 VIP 页 |
| 管理员接口误调 | 同 500 处理（不应出现的调用） |
| 账号被禁 | **全屏拦截页**："您的账号因 XX 原因已被限制使用" + 申诉入口 |

---

### 2.4 404 资源不存在

#### 触发场景
- 用户访问已删除的动态 / 视频
- 匹配对象已销户

#### 防护设计

| 场景 | 行为 |
|---|---|
| 动态不存在 | "该动态已被删除或不存在" + [返回圈子] |
| 视频不存在 | "该视频已被下架" + 自动切换到下一个 |
| 用户主页不存在 | "该用户已注销" + [返回] |

---

### 2.5 请求频率限制（429 Too Many Requests）

#### 触发场景
- 快速连续点击"发送"按钮
- 短时间内大量滑动
- 频繁刷新 feed

#### 防护设计

| 策略 | 实现 |
|---|---|
| 前端防抖 | 按钮点击后 500ms 内 disable（通用 `btn-feedback` + loading 态） |
| 滑动节流 | 卡片滑动动画未完成前不接受新滑动手势 |
| 429 响应处理 | 后端返回 `Retry-After` → 前端倒计时 toast"请求过于频繁，Xs 后再试" |

---

## 三、设备资源场景

### 3.1 内存不足

#### 触发条件
- 低端 Android 设备（2GB RAM）
- 长时间未重启（后台累积）
- 加载大量高清图片 / 视频

#### 防护设计

| 层级 | 检测 | 动作 |
|---|---|---|
| **图片池** | 当前加载图片数 > 20 | 释放离屏图片（`img.src = ''`）|
| **视频节点** | 当前 video 上下文 > 3 | 仅保留当前播放的 video，其余销毁 |
| **feed 列表** | 列表条数 > 50 | 销毁最早 20 条 DOM（虚拟列表兜底）|
| **内存告警** | `uni.onMemoryWarning`（微信） | 立即释放所有非关键资源 + 缓存数据写入 storage |

#### 降级策略

当检测到内存告警：
```
1. 清空 matchStore.cardQueue（除当前卡片）
2. 清空 circleStore.feedList（保留前 5 条）
3. 销毁所有已播放的 video 节点（当前屏除外）
4. 停止预加载队列
5. toast"已自动清理缓存以保证流畅运行"
```

---

### 3.2 存储空间不足

#### 触发条件
- `uni.setStorageSync` 失败（QUOTA_EXCEEDED_ERR）
- 手机剩余存储 < 100MB

#### 防护设计

```ts
// 封装安全存储
export function safeSetStorage(key: string, data: any): boolean {
  try {
    uni.setStorageSync(key, data);
    return true;
  } catch (e) {
    if (e.message?.includes('quota')) {
      // 清理旧缓存后重试
      clearOldCache();
      try { uni.setStorageSync(key, data); return true; } catch { return false; }
    }
    return false;
  }
}

// 在 catch 中给用户提示
if (!safeSetStorage('xfjy_token', token)) {
  ux.fail('存储空间不足，请清理后重试');
}
```

---

### 3.3 电量 / 发热

#### 防护策略
- 低电量（< 20%）时：关闭视频自动预加载
- CPU 发热（持续滑动）：降低动画帧率（从 60fps → 30fps）

---

## 四、权限被拒场景

### 4.1 位置权限

#### 触发场景
- 用户拒绝 `uni.authorize({scope: 'scope.userLocation'})`
- 系统设置中关闭了位置权限

#### 用户感知链路

```
首次请求位置 → 系统弹窗"是否允许心飞交友获取您的位置？"
  ├─ 允许 → 正常获取 ✅
  ├─ 拒绝（单次）→ toast"无法获取位置，部分功能受限" + [去设置]
  └─ 永久拒绝 → 引导用户手动开启（跳转系统设置 / 小程序设置页）
```

#### 防护设计

```ts
// utils/permission.ts（新建）
export async function requestLocationPermission(): Promise<boolean> {
  return new Promise((resolve) => {
    uni.getSetting({
      success: (res) => {
        if (res.authSetting['scope.userLocation'] === false) {
          // 永久拒绝
          uni.showModal({
            title: '需要位置权限',
            content: '在您 settings 中开启位置权限，以便推荐身边的人',
            confirmText: '去设置',
            success: (r) => {
              if (r.confirm) uni.openSetting();
              resolve(false);
            },
          });
        } else if (res.authSetting['scope.userLocation'] === undefined) {
          // 首次
          uni.authorize({
            scope: 'scope.userLocation',
            success: () => resolve(true),
            fail: () => resolve(false),
          });
        } else {
          resolve(true);
        }
      },
      fail: () => resolve(false),
    });
  });
}
```

#### 位置权限缺失时的降级

| 功能 | 有权限 | 无权限 |
|---|---|---|
| 推荐排序 | 综合评分 + 距离衰减 | 仅综合评分（默认城市为"北京市"）|
| 附近的人 | 显示真实距离 | 隐藏距离显示，提示"开启位置权限查看距离" |
| 超级喜欢 | 正常工作 | 允许使用，但推荐不带距离权重 |

---

### 4.2 相机 / 麦克风权限

#### 触发场景
- 认证人脸识别 -> 摄像头
- 通话 -> 摄像头 + 麦克风
- 拍视频上传 -> 摄像头

#### 防护设计

| 权限 | 被拒时文案 | 恢复入口 |
|---|---|---|
| 摄像头 | "需要摄像头权限，以便完成认证" | [去设置] 跳转 `uni.openSetting` |
| 麦克风 | "需要麦克风权限，以便发送语音消息" | [去设置] |
| 相册 | "需要相册权限，以便选择图片" | [去设置] |

#### 截图防御

- 聊天页敏感内容（红包/身份信息）：可配置 `disableScreenshot`（仅限 Android 原生；H5 无法禁用）
- 替代方案：长按菜单"举报" + 水印（userId 半透明铺底）

---

### 4.3 通知权限

#### 触发场景
- 小程序首次订阅消息
- H5 浏览器通知授权

#### 防护设计

```
场景 A：用户拒绝通知
├─ 不做强提醒（避免反感）
├─ 设置页"通知权限已关闭" + [去开启]
└─ 无法接收匹配 / 消息实时推送（但仍可在 App 内查看）

场景 B：用户未做出选择
└─ 在适当节点（发生第一次匹配时）引导开启："开启通知，不错过每一次心动"
```

---

### 4.4 存储 / 相册写入权限

#### 触发场景
- H5 端保存图片到相册（长按"保存图片"）
- 小程序 `wx.saveImageToPhotosAlbum`

#### 防护设计

```ts
export async function saveImageToAlbum(url: string): Promise<boolean> {
  try {
    // 1. 下载
    const { tempFilePath } = await uni.downloadFile({ url });
    // 2. 保存
    await uni.saveImageToPhotosAlbum({ filePath: tempFilePath });
    ux.success('已保存到相册');
    return true;
  } catch (e) {
    if (e.errMsg?.includes('auth')) {
      ux.fail('请授权相册访问权限');
      // 弹出去设置
    } else {
      ux.fail('保存失败，请重试');
    }
    return false;
  }
}
```

---

## 五、账号异常场景

### 5.1 账号被封禁 / 冻结

#### 触发条件
- 管理员后台 `tb_user.status = FROZEN`
- 被多次举报核实
- 系统自动风控（异常滑动 / 批量喜欢）

#### 防护设计

| 状态 | 前端行为 |
|---|---|
| **封禁中** | 所有 API 返回 `code: 403, message: "账号已被封禁"` → 拦截所有导航 → 封禁详情页 |
| **临时冻结** | "账号已被临时冻结，预计 XX:XX 恢复" + 申诉按钮 |
| **永久封禁** | "账号因违反社区规范已被永久封禁" + 申诉入口（kefu 工单）|

#### 封禁详情页 UI

```
┌──────────────────────────────────────┐
│  ⚠️ 账号已受限                         │
│                                      │
│  原因：存在不当言行（于 2026-11-01 由  │
│        社区管理员处理）                 │
│  期限：7 天（预计 2026-11-08 恢复）     │
│                                      │
│  [提交申诉]       [退出登录]           │
│                                      │
│  申诉将在 24 小时内处理                  │
└──────────────────────────────────────┘
```

---

### 5.2 实名认证过期 / 被驳回

#### 防护设计

```
登录成功 → 调用 GET /users/me
         ├─ verified: true → 正常使用
         ├─ verified: false + 未申请 → 首次登录引导实名认证
         └─ verified: false + 已驳回 → toast"实名认证未通过：XXX" + [重新认证]
```

---

### 5.3 异地登录 / 安全验证

#### 触发条件
- 后端检测到同一账号异地 multiple 设备登录

#### 防护设计

```
异地登录 → 后端踢出旧 token → 旧设备收到 401 + message: "账号在异地登录"
        → 前端弹出 modal："您的账号已在其他设备登录，如非本人操作请修改密码"
        → [退出] / [重新验证]
```

---

## 六、数据边界场景

### 6.1 超长内容

| 字段 | 当前限制 | 建议限制 | 超出提示 |
|---|---|---|---|
| 昵称 | maxlength=20 | maxlength=20 | UI 阻止输入（无需提示）|
| 签名 | 无校验 | maxlength=100 | toast"签名不超过 100 字" |
| 动态正文 | 无校验 | maxlength=500 | toast"动态内容不超过 500 字" |
| 评论 | 无校验 | maxlength=200 | toast"评论不超过 200 字" |
| 图片上传 | 单文件？ | 单文件 ≤ 10MB，最多 9 张 | "图片过大，请选择 10MB 以内的文件" |
| 视频上传 | 无校验 | 单文件 ≤ 30MB，≤ 60s | "视频过大或过长" |
| 语音 | 无校验 | ≤ 60s | 录音达 55s 时倒计时提示 |

---

### 6.2 极短内容

| 场景 | 当前 | 建议 |
|---|---|---|
| 签名为空 | 允许保存为空 | 无影响（资料完整度降分即可）|
| 动态正文纯空格 | 允许 | trim 后校验："请输入动态内容" |
| 评论纯表情包 | 允许 | 允许（纯表情也是表达）|
| 搜索输入为空格 | 触发搜索 | trim 后为空不触发搜索 |

---

### 6.3 极端输入

| 输入 | 处理 |
|---|---|
| emoji | 确保 DB 为 utf8mb4（技术约束 CONST-DATA-002）|
| 特殊字符（< > ' "） | 前端 XSS 过滤 + 后端参数化 SQL |
| 零宽字符 | trim 时保留（某些 emoji 组合需要）；存储 length 校验归一化 |
| 粘贴大段文本 | 自动截断到 maxlength 并 toast"已自动截断至 X 字" |

---

### 6.4 并发 / 竞态

#### 竞态场景

| 场景 | 问题 | 方案 |
|---|---|---|
| 快速点击"喜欢"两次 | 后端可能重复扣减滑动次数 | 按钮 debounce 500ms + 乐观 UI |
| 发布动态后立即切页面 | 图片上传尚未完成 | 发布按钮 loading 直到所有图片上传完毕 |
| 支付后立即退出订单页 | 订单状态轮询未完成 | 订单创建后置本地 pending 队列，切回"我的订单"时继续轮询 |
| feed 数据更新时滑动 | 列表 index 错乱 | 使用 skeleton 锁定列表；加载完成后再解锁 |
| token 刷新瞬间多请求 401 | 当前已做并发去重 ✅ | 保留 |

---

## 七、生命周期异常场景

### 7.1 App 进入后台

#### 处理要点

```ts
uni.onAppHide(() => {
  // 1. 暂停预加载队列
  pausePreload();
  // 2. 断开 IM 长连接（省流量 + 省电）—— 或在心跳保活和断开间选
  // 推荐策略：保活 30s 后断开；前台 instantly 重连
  imDisconnectTimer = setTimeout(() => { logoutIM(); }, 30000);
});
```

#### 后台恢复

```ts
uni.onAppShow(() => {
  clearTimeout(imDisconnectTimer);
  // 1. 断线重连 IM
  if (!isIMLoggedIn() && getToken()) {
    loginIMDirect(String(userStore.userInfo?.id || ''));
  }
  // 2. 重新获取网络状态（网络可能在后台期间切换）
  syncNetworkStatus();
  // 3. 静默上报：后台停留时长、断连次数（埋点）
  // 4. 检查 token 是否过期（后台可能过期）
  if (isTokenExpired()) handleUnauthorized();
});
```

---

### 7.2 App 被系统杀进程

#### 触发条件
- 用户手动划掉 App
- 系统内存不足强杀
- 用户重启手机

#### 恢复策略

| 数据 | 恢复方式 |
|---|---|
| 登录态 | `uni.getStorage('xfjy_token')` → 有则恢复，无则跳登录 |
| 当前页面 | 不做恢复（冷启动定格登录页 / 首页，避免恢复断裂栈）|
| IM 状态 | 冷启动后 App.vue onShow 重连 ✅ |
| 未发送消息 | 无缓存（需增加草稿功能 → 排期）|

---

### 7.3 版本升级

#### 强制升级（后端返回 `versionCode < minVersionCode`）

```
所有请求 header 携带当前 versionCode
后端响应 header X-App-Upgrade-Required: true + body 变更
前端拦截 → 显示全屏升级弹窗：
"心飞交友 v2.0 已发布，需要升级后才能继续使用"
[立即更新] 按钮：
  - 小程序：调用 uni.getUpdateManager() 下载新版本并重启
  - H5：自动刷新页面（CDN 缓存新版本已部署）
  - App（原生包装）：跳转 App 商店
```

#### 推荐升级（非强制）

```
"v2.0 已发布，包含以下新功能：[新功能列表]"
[稍后更新] [立即更新]
```

---

## 八、支付异常场景

### 8.1 支付中断

| 中断时机 | 后端状态 | 前端行为 |
|---|---|---|
| 用户取消支付 | 订单 PENDING | toast"支付已取消" + 保留订单"待支付" |
| 网络断开 | 订单 PENDING | 订单列表显示"待支付，点击继续" |
| App 被杀 | 订单 PENDING | 重启后进入 VIP 页检测 pending 订单并轮询 |
| 银行扣款但回调丢失 | 订单 PENDING 但实际已扣 | 后端定时任务对账后自动确认 + 前端状态同步 |

### 8.2 重复支付

```
幂等保障：前端调支付前先生成 clientOrderId → 后端 tb_pay_order 唯一下单
再次调起支付 → 后端检测到同一 clientOrderId 已成功 → 直接返回成功
```

### 8.3 退款

```
退款申请入口 → 选择退款原因 → 提交工单（tb_order_refund 表）
          ↓
     后台审核 → 3 个工作日到账
     前端状态: "退款处理中" → "已退款 ¥XX"
```

---

## 九、IM 异常场景

### 9.1 连接断开

| 断开原因 | 前端行为 |
|---|---|
| 网络切换（WiFi → 4G） | 自动重连，顶部显示"正在重连…"（1.5s 后消失）|
| 服务端重启 | 指数退避：1s → 2s → 4s → 8s → 30s（最大间隔） |
| 心跳超时 | 主动断开 + toast"消息服务连接断开，正在重连…" |
| 被踢下线（异地登录） | 弹窗"账号在异地登录" + [重新登录] / [退出] |

### 9.2 消息发送失败

```
发送流程：
  本地入列（status: 'sending'）→ 发送 → 状态更新
                              ├─ success → status: 'sent'
                              ├─ timeout → status: 'failed' + 红点
                              └─ explicit fail → status: 'failed'
failed 消息：点击气泡 → showActionSheet（重发 / 删除）
```

### 9.3 大消息 / 超长文本

```
文本消息长度 > 5000 字 → 前端直接 split 多条发送（附 seq 标记，接收端合并）
图片消息 > 10MB → 前端压缩（max 1080p / quality 0.7）+ 仍超限则提示
语音消息 > 60s → 前端限制录音时长，到 55s 开始倒计时
```

---

## 十、全局错误边界

### 10.1 Error Boundary（Vue 级错误捕获）

```ts
// error-boundary.vue（新建）
<template>
  <slot v-if="!hasError"></slot>
  <view v-else class="error-boundary">
    <text class="error-icon">⚠️</text>
    <text class="error-title">出错了</text>
    <text class="error-message">{{ message }}</text>
    <button class="retry-btn" @click="reset">重新加载</button>
    <button class="report-btn" @click="report">反馈问题</button>
  </view>
</template>

<script setup lang="ts">
import { onErrorCaptured, ref } from 'vue';
const hasError = ref(false);
const message = ref('');

onErrorCaptured((err) => {
  hasError.value = true;
  message.value = import.meta.env.DEV ? String(err) : '页面加载异常';
  // 上报错误日志
  logError('component', err);
  return false; // 阻止向上传播
});

function reset() {
  hasError.value = false;
  message.value = '';
}
</script>
```

### 10.2 JS 全局异常捕获

```ts
// main.ts
app.config.errorHandler = (err, instance, info) => {
  console.error('[全局异常]', err, info);
  // 上报到 Sentry / 自建 ELK
  logError('global', err);
};

// 小程序未捕获 Promise rejection
uni.onUnhandledRejection((res) => {
  console.error('[未处理的 Promise]', res.reason);
  logError('promise', res.reason);
});
```

---

## 十一、统一反馈组件规范

### 11.1 Toast 规范

| 类型 | icon | 显示时长 | 使用场景 |
|---|---|:---:|---|
| `success` | ✓ | 2s | 操作成功（发布、发送、保存）|
| `fail` | ✗ | 3s | 操作失败（上传、支付、网络）|
| `warn` | ⚠ | 3s | 警告（权限被拒、即将截止）|
| `info` | ℹ | 2s | 普通提示（网络恢复、缓存清理）|
| `loading` | spinner | 手动 hide | 阻塞等待（登录、支付、上传）|

### 11.2 弹窗规范（showModal）

| 场景 | 标题 | 按钮 | 备注 |
|---|---|---|---|
| token 过期 | "登录已过期" | [重新登录] / [稍后] | 必须 |
| 注销账号 | "确认注销？" + sub"数据不可恢复" | [确认注销] [取消] | 强破坏性操作 |
| 删除内容 | "确认删除？" | [删除] [取消] | 二级操作 |
| 网络恢复 | "网络已恢复" | [点击刷新] | 自动消失 3s |
| 被封禁 | "账号受限" | [申诉] [退出] | 全屏拦截 |
| 支付中断 | "订单待支付" | [继续支付] [稍后] | 不取消订单 |

### 11.3 全屏错误页规范

```
┌────────────────────────────────────────┐
│                                        │
│             [动画插画]                    │
│                                        │
│          服务器繁忙 / 页面飞走了            │
│                                        │
│     请稍后再试，或检查您的网络设置          │
│                                        │
│         [🔄 重新加载]                     │
│                                        │
│    Trace ID：XXXX-XXXX (长按复制)          │
│                                        │
└────────────────────────────────────────┘
```

---

## 十二、落地清单

### 12.1 新增工具文件

| 文件 | 职责 | 优先级 |
|---|---|:---:|
| `src/utils/network-monitor.ts` | 网络状态监听 + NetworkBar 显隐 | P0 |
| `src/utils/ux.ts` | 统一消息反馈封装（ux.success / ux.fail / ux.info / ux.networkError）| P0 |
| `src/utils/permission.ts` | 位置 / 相机 / 麦克风 / 相册权限请求封装 | P1 |
| `src/components/network-status-bar.vue` | 顶部网络状态指示条 | P0 |
| `src/components/error-boundary.vue` | 错误边界组件 | P1 |

### 12.2 改造已有文件

| 文件 | 改造点 | 优先级 |
|---|---|:---:|
| `src/utils/request.ts` | 增加超时处理 / 错误分类 / 断网前校验 | P0 |
| `src/App.vue` | 接入 network-monitor / 监听 onAppHide/onShow | P0 |
| `src/store/match.ts` | 增加 pendingLikeQueue / 断网恢复后自动上报 | P1 |
| `src/store/chat.ts` | 增加发送失败重连队列 / 消息发送状态管理 | P1 |
| `src/pages/match/index.vue` | 断网时显示 NetworkBar + 滑动停止 | P1 |
| `src/pages/chat/chat.vue` | 网络断开时输入区 disable + 顶部提示 | P1 |
| `src/pages/circle/index.vue` | 加载失败 retry + loading 占位 | P1 |
| `src/pages/mine/vip.vue` | 支付中断恢复 + 错误重试 | P1 |

### 12.3 各场景用户提示速查表

| 场景 | P级 | 提示文案 | 恢复动作 |
|---|---|---|:---:|
| 断网 | P0 | "网络连接已断开，请检查网络设置" | 自动重连 |
| 弱网 | P1 | "网络环境较差，已为您显示缓存数据" | 点击刷新 |
| 超时 | P1 | "请求超时，请点击重试" | 重试 |
| 500 | P0 | "服务器繁忙，请稍后再试" | 重试 |
| 401 | P0 | "登录已过期，请重新登录" | 跳登录 |
| 403 | P1 | "账号已被限制使用，如有疑问请反馈" | 申诉 |
| 404 | P1 | "内容已被删除或不存在" | 返回 |
| 429 | P2 | "请求过于频繁，请 Xs 后再试" | 倒计时 |
| 内存告警 | P1 | "已自动清理缓存以保证流畅" | 自动恢复 |
| 存储满 | P1 | "存储空间不足，请清理后重试" | 清理 |
| 权限被拒(位置) | P1 | "请开启位置权限，以便推荐身边的人" | 跳设置 |
| 权限被拒(相机) | P1 | "请开启摄像头权限，以便完成认证" | 跳设置 |
| 权限被拒(通知) | P2 | "开启通知，不错过每一次心动" | 跳设置 |
| 图片过大 | P2 | "图片过大，请选择 10MB 以内的文件" | 重选 |
| 视频过大 | P2 | "视频过大或过长，请选择 ≤30MB / ≤60s" | 重选 |
| 内容超长 | P2 | "签名不超过 100 字" | 截断/阻止 |
| 服务端维护 | P0 | "系统将于 XX:XX 恢复服务" | 等待 |

---

## 十三、变更记录

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1.0 | 2026-11-01 | 初版。覆盖网络/服务端/设备/权限/账号/数据/生命周期/支付/IM/错误边界共 10 大类 47 个异常场景，输出 P0 需做文件 5 个 + 改造已有文件 8 个 + 场景速查表 |
