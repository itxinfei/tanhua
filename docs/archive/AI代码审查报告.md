> **已失效（2026-11-03）**
> 失效原因：与 `22-代码审查报告-全模块.md` 主题重复且时点更早（未标注审查日期），
> 部分结论已被 22 号报告与 `01-需求文档.md` §11.7 取代；文件位于仓库根目录属误放。
> 实际状态：仍成立的缺口以 §11.7（1~32）为准；本文件仅作历史留存，**不得引用其统计数字**。

# 心飞交友全项目 AI 代码审查报告

> 审查范围：后端 10 模块（xfjy-server/xfjy-api + 8 域服务 + sso + gateway）+ 前端 2 项目（xfjy-admin / xfjy-app）
> 审查模式：纯只读，未修改任何代码
> 结论：**后端 117 项（致命 10 / 严重 35 / 中等 38 / 轻微 34），前端 9 项（致命 1 / 严重 3 / 中等 3 / 轻微 2）**

---

## 一、致命问题清单（上线必炸，P0 必修）

| # | 位置 | 问题 | 复现路径 |
|---|------|------|----------|
| 1 | `xfjy-app/src/utils/wukongim.ts:235` + `App.vue:45` + `chat.vue:516` + `kefu/chat.vue:224` | **IM token 前端本地派生可伪造**：`xfjy_${userId}_im_token` 完全可预测，任何人知道 userId 即可冒充任意用户连接 WuKongIM 收发消息。3 个生产调用点全部走此路径，无一走服务端凭证 | 构造 token 直连 ws://host:5200 |
| 2 | `xfjy-sso UserService:44` | **万能验证码 "123456"**：DEV_MASTER_CODE 命中即登录任意手机号，无环境守卫 | 任意手机号 + 123456 → 登录成功 |
| 3 | `xfjy-server AdminUserService` | buildUserFilterWrapper 返回 null 未判空 → NPE | 后台用户列表特定筛选条件 |
| 4 | `xfjy-server AdminReviewController` | 批量驳回 `(List<Long>)` Jackson 反序列化 Integer → ClassCastException（同文件 batchApprove 强类型是对的——同文件两套写法） | 后台批量驳回 |
| 5 | `xfjy-server MyServletRequestWrapper` | isReady() 逻辑反 + onAllDataRead 过早调用 → 异步读卡死 | RequestBody 缓存读取 |
| 6 | `xfjy-user/circle/video` | page/pageSize `required=false` 却 `page-1` → null 拆箱 NPE（6 处）；`new ObjectId(可空)` 无判空 10+ 处 | 缺参调用分页接口 |
| 7 | `xfjy-circle` | commentType==1 拆箱 NPE；type==2 拆箱 NPE 发生在 save 之后 → 评论已落库返 false → 重试重复评论 | 评论接口 type 传 null |
| 8 | `xfjy-match RecommendUserApiImpl:76` | score 为 Double null 拆箱 NPE | 推荐列表 score 为 null 的用户 |
| 9 | `xfjy-sso UserService` | changePhone 先查后写非原子 → 并发双占手机号 | 并发换绑同一手机号 |
| 10 | `xfjy-app/src/utils/pay.ts:35` | **PAYMENT_MODE="mock" 硬编码**：mock 渠道直接调后端 confirmVipPay 开通 VIP。若此构建上生产 = 免费开通 VIP（取决于后端 confirm 是否校验真实支付回调，后端 PayApiImpl 已有先查后写非原子问题） | 前端直接调 confirmVipPay |

## 二、严重问题清单（安全/功能缺陷，P1）

### 安全类
- **凭证硬编码全模块蔓延**：10 个 yml 硬编码 nacos/nacos；MySQL root/123456 ×4；Redis itxinfei123456；环信 client-secret 明文入库（需清 git 历史）
- **internal token 全 10 模块统一** `-xfjy-internal-dev-token-change-in-prod`，YAML `-` 前缀陷阱全模块存在
- `xfjy-gateway CorsConfig`：allowedOriginPattern("*") + allowCredentials(true) → 任意源携凭证跨域
- `xfjy-gateway RateLimit`：信任 X-Forwarded-For 首段 → 伪造绕过限流
- `xfjy-message WuKongImApiImpl`：getUserToken 返明文 password；WuKongIM API 无鉴权头 → 内网越权
- `xfjy-server TokenInterceptor`：ZSET 只写不过期 → 内存泄漏；token 拼 URL 路径且日志打印明文
- `xfjy-admin/src/utils/logger.ts`：reportError 被 ENABLE_LOG 开关控制 → 设 false 则 Vue errorHandler/window.onerror/unhandledrejection 全部静默吞异常；.env.production 未设 VITE_ENABLE_LOG=false → 生产所有请求日志（含 data 载荷）打印到 console
- `xfjy-app` **缺 .env.production** → 生产构建回退 `https://api.xfjy.com`（域名是否解析/备案未知）；admin 有 .env.production 但值为空，两项目配置管理不一致

### 功能缺陷类
- `xfjy-server AdminUserService:295`：重置密码 md5("123456")
- `xfjy-server IMService`：分页恒 0（同文件一处改了另一处没改——两套写法）
- `xfjy-server` CSV 导出：mobile 写两列、昵称丢失（复制粘贴忘改）
- `xfjy-pay PayApiImpl`：summary 同列双 eq(status) → paidCount 恒 0；毫秒戳比 datetime 列 → 日期过滤失效；openVip 先查后写非原子 → 并发撞 uk 回滚
- `xfjy-message HuanXinApiImpl`：吞异常返 void → 消息静默丢失；HuanXin/WuKong new RestTemplate 无超时 → 线程池耗尽（对比 match 配 5s 超时——不一致）
- `xfjy-recommend QuanZiMsgConsumer`：findById 可 null → publish.getText() NPE；catch 未传异常 e 丢堆栈（同模块 VideoMsgConsumer 传了——不一致）
- `xfjy-recommend` 评分逻辑：length=0 落入 else 得 3 分，高于 1-50 字的 1 分 → 逻辑错
- `xfjy-elasticsearch UserLocationService`：address 为 null 仍 map.put 清空已存地址
- `xfjy-app/src/utils/wukongim.ts`：getChatHistory 恒返回 [] → 历史消息功能死；wsAddr 缺省回退 ws://127.0.0.1:5200
- `xfjy-admin/views/login/index.vue`：DEV mock 登录写 mockToken+SUPER_ADMIN 到 localStorage（编译时守卫可 tree-shake，但模式风险）

## 三、中等问题（P2，体验/健壮性）

- `xfjy-app/src/utils/request.ts`：upload() 与 request() 状态码处理 90% 复制粘贴重复（与后端 PushService 同源 LLM 模式）
- `xfjy-app/src/store/user.ts`：fetchUserInfo verified/vip/photoAlbum/completeness 永远取本地旧值，后端字段被丢弃
- `xfjy-admin/components/AppTabbar.vue`：px 单位而非 rpx（CLAUDE.md 违规，tabbar 固定布局可能有意为之）
- 后端：`UsersApiImpl` 按 created 排序但只有 date 字段（复制 UserLike 忘改）；followUser 无判重直接 save；INSERT IGNORE 无唯一索引 → 幂等失效；@Cacheable 只 import 未用；ESUserLocationApiImpl 非 200 返 null 违 List 契约；PageRequest.of 无下界 6 处；拦截器日志打印 token 明文

## 四、轻微问题（P3）

- `xfjy-app/.env.development` 硬编码局域网 IP 192.168.20.133（开发环境可接受）
- 后端 34 项：死 import、未用变量、注释与代码不符、命名不规范等（详见各模块审查记录）

---

## 五、系统性缺陷模式归纳（大模型共性问题 6 类）

| 模式 | 典型案例 | 出现频次 |
|------|----------|----------|
| **1. 参数无防御 → null 拆箱 NPE** | page-1、score 拆箱、ObjectId(可空) | 后端 20+ 处 |
| **2. 复制粘贴忘改字段** | CSV mobile 双写、UsersApiImpl created/date、PayApiImpl 双 eq(status) | 5+ 处 |
| **3. 同文件两套写法矛盾** | batchApprove 强类型 vs 批量驳回弱类型、IMService 分页一处改一处没改、wukongim loginIMDirect vs getImCredential | 6+ 处 |
| **4. 硬编码凭证/配置** | 10 yml + 万能验证码 + internal token + PAYMENT_MODE=mock | 全模块蔓延 |
| **5. 死代码/死功能** | getChatHistory 恒空、@Cacheable 未用、catch 丢堆栈 | 10+ 处 |
| **6. 先查后写非原子** | changePhone、openVip、followUser | 3+ 处 |

## 六、亮点确认（质量较高，修复时勿破坏）

- **前端组件化**：a11y-button/a11y-dialog/empty-state/error-boundary/skeleton 等 11 个通用组件，aria 无障碍完善
- **前端样式系统**：CSS 变量统一管理主色 #fe3c72，旧色 #ff5e9c 两项目零残留，间距/字号刻度化，WCAG 对比度修正
- **前端三态齐备**：skeleton 加载 / empty-state 空数据 / error-boundary 错误态三组件全覆盖
- **前端 console 零泄漏**：仅 7 处全在异常捕获兜底或 logger 封装内
- **xfjy-admin 核心层**：request.ts 401 双路径、auth store logout 容错、router 角色守卫——质量高于后端
- **xfjy-app 核心层**：401 并发去重、402 付费墙、429 限流、signOutAll 清理完整
- **后端亮点**：mapper 无 SQL 注入、PayController 越权防护、RecommendScoringService、GlobalExceptionHandler、savePublish 补偿回滚、Pattern.quote 防正则注入、pageSize 上限 100、fail-closed 拦截器、LoginLimitService 三层防线、UserPurgeTask PII 匿名化、ES geo_distance 标准写法、MQ 消费 Redis 幂等去重、pay CAS 条件更新/Lua 原子限次/金额 Long 分

## 七、修复优先级建议

1. **P0（上线前必修）**：致命 10 项——IM token 改服务端下发、删万能验证码、NPE 系列加防御、mock 支付加环境守卫
2. **P1（上线前应修）**：凭证外置 Nacos/环境变量、CORS 收紧、RestTemplate 统一超时、logger 生产关日志但保异常上报、补 .env.production
3. **P2（迭代修）**：复制粘贴重复合并、死功能补实现或删承诺、排序字段修正
4. **P3（随手修）**：死 import、命名、注释

---
*审查完成时间：2026-11-02 | 纯只读审查，未修改任何代码*