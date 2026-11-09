<div align="center">

<img src="docs/logo/logo-horizontal-transparent.png" width="360" alt="心飞交友">

**心飞交友（xfjy）**

陌生人社交平台，自托管部署。匹配、圈子、短视频、IM、会员、内容审核都在里面，IM 和音视频用的开源方案，不按连接数付授权费。

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen?logo=springboot&logoColor=white)
![JDK](https://img.shields.io/badge/JDK-25%20LTS-orange?logo=openjdk&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.3-green?logo=spring&logoColor=white)
![Nacos](https://img.shields.io/badge/Nacos-3.x-2C84EA?logo=alibabacloud&logoColor=white)
![Sa-Token](https://img.shields.io/badge/Sa--Token-1.46.0-red)
![MyBatis Plus](https://img.shields.io/badge/MyBatis%20Plus-3.5.17-blue)
![WuKongIM](https://img.shields.io/badge/WuKongIM-长连接-ff6b35?logo=wechat&logoColor=white)
![LiveKit](https://img.shields.io/badge/LiveKit-SFU%20音视频-563dff?logo=livekit&logoColor=white)
![Vue.js](https://img.shields.io/badge/Vue-3.5-42b883?logo=vue.js&logoColor=white)
![uni-app](https://img.shields.io/badge/uni--app-Vue3-purple)
![Element Plus](https://img.shields.io/badge/Element%20Plus-2.14-blue)
![ECharts](https://img.shields.io/badge/ECharts-6-006699?logo=apacheecharts&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)
![Pinia](https://img.shields.io/badge/Pinia-4-F7D336?logo=vue.js&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-7.0-47A248?logo=mongodb&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.x-DC382D?logo=redis&logoColor=white)
![ES](https://img.shields.io/badge/Elasticsearch-9.x-005571?logo=elasticsearch&logoColor=white)
![RocketMQ](https://img.shields.io/badge/RocketMQ-5.x-D773A2?logo=apache-rocketmq&logoColor=white)
![MinIO](https://img.shields.io/badge/MinIO-S3-FF2389?logo=minio&logoColor=white)
![Qdrant](https://img.shields.io/badge/Qdrant-向量检索-4285F4)
![Neo4j](https://img.shields.io/badge/Neo4j-5.x%20图数据库-008CC1?logo=neo4j&logoColor=white)
![PyTorch](https://img.shields.io/badge/PyTorch-双塔模型-EE4C2C?logo=pytorch&logoColor=white)

</div>

---

## 目录

- [核心亮点](#核心亮点)
- [数字说话](#数字说话)
- [产品功能预览](#产品功能预览)
- [技术选型](#技术选型)
  - [后端](#后端)
  - [前端](#前端)
- [内容安全](#内容安全)
- [推荐引擎](#推荐引擎)
- [系统架构](#系统架构)
- [安全设计](#安全设计)
- [部署运维](#部署运维)
- [项目结构](#项目结构)
- [环境变量](#环境变量)
- [快速启动](#快速启动)
- [监控与可观测](#监控与可观测)
- [路线图](#路线图)
- [文档](#文档)
- [贡献指南](#贡献指南)
- [商业支持](#商业支持)

---

<a id="核心亮点"></a>
## 核心亮点

<table>
  <tr>
    <td align="center" width="33%"><b>探花匹配</b><br/><span>左滑跳过，右滑喜欢，还有超级喜欢<br/>互相喜欢就弹「It's a Match!」</span></td>
    <td align="center" width="33%"><b>即时通讯</b><br/><span>WuKongIM 自托管长连接，按自己的机器给连接数<br/>LiveKit SFU 已就绪，1v1 通话接口在 P3</span></td>
    <td align="center" width="33%"><b>推荐</b><br/><span>5 层召回漏斗，双塔向量召回<br/>再叠一层 Neo4j 二度好友</span></td>
  </tr>
  <tr>
    <td align="center" width="33%"><b>内容安全</b><br/><span>文本走 DFA 敏感词，图视先发后审<br/>三级定级，词库改完不用重启</span></td>
    <td align="center" width="33%"><b>附近的人</b><br/><span>ES geo 500km 召回<br/>实测 &lt;50ms</span></td>
    <td align="center" width="33%"><b>一套代码 13 端</b><br/><span>H5、App、各平台小程序<br/>鸿蒙、快应用</span></td>
  </tr>
  <tr>
    <td align="center" width="33%"><b>会员</b><br/><span>套餐、支付订单、收入统计<br/>一条链路走完</span></td>
    <td align="center" width="33%"><b>短视频</b><br/><span>上下滑的视频流<br/>手势和抖音一致</span></td>
    <td align="center" width="33%"><b>审核工作台</b><br/><span>A 通过、R 驳回<br/>人工处理时联动自动下架</span></td>
  </tr>
</table>

---

<a id="数字说话"></a>
## 数字说话

<table>
  <tr>
    <td align="center"><span style="font-size:28px;font-weight:700;line-height:1.5">12</span><br/>微服务模块</td>
    <td align="center"><span style="font-size:28px;font-weight:700;line-height:1.5">13</span><br/>端统一输出</td>
    <td align="center"><span style="font-size:28px;font-weight:700;line-height:1.5">12</span><br/>中间件容器</td>
    <td align="center"><span style="font-size:28px;font-weight:700;line-height:1.5">6</span><br/>核心域服务</td>
  </tr>
  <tr>
    <td align="center"><span style="font-size:28px;font-weight:700;line-height:1.5">5 层</span><br/>推荐召回漏斗</td>
    <td align="center"><span style="font-size:28px;font-weight:700;line-height:1.5">64 维</span><br/>用户 Embedding</td>
    <td align="center"><span style="font-size:28px;font-weight:700;line-height:1.5">&lt;50ms</span><br/>地理召回延迟</td>
    <td align="center"><span style="font-size:28px;font-weight:700;line-height:1.5">¥0</span><br/>IM / 音视频授权费</td>
  </tr>
</table>

口径说明：中间件按常驻容器算，不含 `minio-init` 这个一次性任务；核心域服务指 user / match / circle / video / message / pay 六个，`xfjy-server` 是 BFF，`recommend`、`elasticsearch` 按能力组件另算。

---

<a id="产品功能预览"></a>
## 产品功能预览

下面都是跑起来之后截的，不是设计稿。移动端 H5 和运营后台各一组。

### 移动端 App（uni-app，多端同源）

<p align="center">
  <img src="xfjy-app/docs/demo/app-demo.gif" width="320" alt="心飞交友移动端 H5 操作演示：匹配首页与底部 Tab 切换"/>
  <br/>
  <sub>匹配首页 → 圈子 / 视频 / 我的 → 回到首页</sub>
</p>

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/app/01-login.png" width="200"/><br/><b>登录注册</b><br/>手机号 + 验证码</td>
    <td align="center"><img src="docs/screenshots/app/02-match.png" width="200"/><br/><b>探花匹配</b><br/>左滑跳过 · 右滑喜欢 · 超级喜欢</td>
    <td align="center"><img src="docs/screenshots/app/03-match-success.png" width="200"/><br/><b>配对成功</b><br/>互喜弹「It's a Match!」</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/app/04-circle.png" width="200"/><br/><b>圈子动态</b><br/>好友 / 推荐双 Tab</td>
    <td align="center"><img src="docs/screenshots/app/05-publish.png" width="200"/><br/><b>发布内容</b><br/>图文 / 视频 + 话题 / 位置</td>
    <td align="center"><img src="docs/screenshots/app/06-video.png" width="200"/><br/><b>短视频</b><br/>上下滑视频流</td>
  </tr>
  <tr>
    <td align="center" colspan="3"><img src="docs/screenshots/app/07-mine.png" width="200"/><br/><b>个人中心</b><br/>会员权益 · 好友 · 谁看过我</td>
  </tr>
</table>

配对成功的庆祝层不是纯动画：双方互喜时弹层，同时建 IM 好友关系、推匹配通知、通过 IM 实时透一条匹配消息出去。上图是真实渲染（对方头像是占位图，右侧是品牌图标）。

### 运营后台（Vue3 + Element Plus + ECharts）

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/admin/00-login.png" width="460"/><br/><b>登录页</b><br/>账号 + 密码（DEV 环境标识）</td>
    <td align="center"><img src="docs/screenshots/admin/01-dashboard.png" width="460"/><br/><b>数据看板</b><br/>总览 + 趋势 + 实时动态</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/admin/02-user.png" width="460"/><br/><b>用户管理</b><br/>125+ 用户 · 封禁 / 重置密码 / 导出</td>
    <td align="center"><img src="docs/screenshots/admin/03-movement.png" width="460"/><br/><b>动态管理</b><br/>CRUD + 图片 / 视频</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/admin/04-video.png" width="460"/><br/><b>视频管理</b><br/>小视频审核 / 下架</td>
    <td align="center"><img src="docs/screenshots/admin/05-review.png" width="460"/><br/><b>内容审核</b><br/>A 通过 / R 驳回</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/admin/06-vip.png" width="460"/><br/><b>VIP 套餐</b><br/>权益配置</td>
    <td align="center"><img src="docs/screenshots/admin/07-pay.png" width="460"/><br/><b>支付订单</b><br/>订单 / 收入统计</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/admin/08-kefu.png" width="460"/><br/><b>客服工作台</b><br/>IM 会话 + 反馈 + FAQ</td>
    <td align="center"><img src="docs/screenshots/admin/09-system.png" width="460"/><br/><b>系统管理</b><br/>管理员 / 日志 / IM 通道</td>
  </tr>
</table>

---

<a id="技术选型"></a>
## 技术选型

<a id="后端"></a>
### 后端

| 领域 | 选型 | 说明 |
|------|------|------|
| 后端框架 | Spring Boot 4.1.1 + JDK 25 + Spring Cloud 2025.1 | 跟当前主线版本，`jakarta.*` 命名空间，没留 javax 兼容层 |
| 微服务生态 | Spring Cloud Alibaba 2025.1，Nacos 3.0.0 只做服务发现 | 配置没进 Nacos，各模块自己的 `application.yml`。改配置要重启，换来排查简单 |
| 服务网关 | Spring Cloud Gateway（WebFlux，`spring-cloud-starter-gateway-server-webflux`） | 对外只开 8090，路由、限流、跨域、InternalToken 都收在这一层 |
| 认证鉴权 | Sa-Token 1.46.0（spring-boot4-starter，Redis 会话只在 sso 引入） | Sa-Token 管登录态签发和校验；业务鉴权走自研拦截器链（`TokenInterceptor`、`AdminTokenInterceptor`、`XInternalTokenInterceptor`） |
| 持久层 | MyBatis-Plus 3.5.17（spring-boot4-starter）+ MySQL 8.0 | 单表 CRUD 交给 MP，复杂查询写 XML。服务间调用用 `@LoadBalanced`，地址不写死 |
| 文档数据库 | MongoDB 7（compose `mongo:7`）+ spring-data-mongodb | 动态、评论、消息、行为日志这类 Schema 会变的数据放这里；MySQL 只留强约束的关系表 |
| 搜索引擎 | Elasticsearch 9.4.5 + spring-data-elasticsearch | 「附近的人」走 geo 查询，没套 MySQL geo 函数。实测召回 <50ms |
| 缓存 | Redis 7（`redis:7-alpine`）+ Spring Cache + Lettuce | 会话、热点缓存、网关限流计数、用户风控计数，共用一套 Redis |
| 消息队列 | RocketMQ 5.3.1（starter 2.3.6） | 发动态、发小视频不阻塞主链路，行为评分异步丢给 `xfjy-recommend` 消费 |
| 对象存储 | MinIO（S3 兼容） | 图片视频存 MinIO，本地和云上同一套 S3 接口，不绑厂商 |
| 即时通讯 | WuKongIM（Apache-2.0，自托管 WS 长连接）+ LiveKit SFU（WebRTC） | 换掉了商业 IM 和音视频 SDK，连接数取决于自己的机器 |
| 向量数据库 | Qdrant（纯 REST，point id 直接用 userId） | 双塔 embedding 存这里，检索不用维护 id 映射；挂掉就降级回原召回 |
| 图数据库 | Neo4j 5.x（Bolt 原生驱动） | 二度好友、共同喜欢数走图查询，放 MySQL 要多轮 join |
| AI 模型 | PyTorch 双塔（权重共享 + BPR Loss + L2 归一化） | 出 64 维 embedding。管线带 `--mock`，不装依赖也能验证一遍 |
| 密码安全 | spring-security-crypto（BCrypt，强度 10） | 存的是 BCrypt 哈希，不用 MD5，盐值在哈希串里 |
| 服务发现 | `@LoadBalanced RestClient.Builder` | `lb://` 服务名做负载均衡，IP 和域名不进代码（`HttpServiceProxyConfig`） |
| 限流 | 网关 `RateLimitGlobalFilter`（Redis 计数，5 次/60s）+ `RiskControlService`（用户级固定窗口） | 两层：网关按 IP 挡一道，服务内按 userId 再挡一道 |
| 监控 | Spring Boot Actuator + Micrometer（仅 server 引入） | 开 health / info / metrics 三个端点，够 K8s 探针用。没上 Prometheus |
| 代码质量 | Lombok + commons-io / commons-lang3 + 统一 Result 异常体系 | 业务异常只有 `BusinessException` 一条路，兜底在 `GlobalExceptionHandler` |
| 部署运维 | Git Tag 语义版本 + Gitea Actions | 打 tag 就出精确版本镜像，回滚到指定 tag。增量编译，部署十几秒 |
| 敏感词引擎 | houbb/sensitive-word（DFA 自动机，支持多音字、繁简、字符混淆） | 三级定级：直接拒、星号替换、进人工队列。词库热刷新 |

<a id="前端"></a>
### 前端

| 项目 | 框架栈 | 说明 |
|------|--------|------|
| **xfjy-app** | uni-app + Vue 3.5 + Pinia + Sass | 一套代码出 13 端：H5、App、微信 / 支付宝 / 抖音 / QQ / 百度 / 快手 / 飞书 / 小红书 / 京东 小程序、鸿蒙、快应用 |
| **xfjy-admin** | Vue 3.5 + TS + Element Plus 2.14 + ECharts 6 + Vite 8 + Pinia | 运营后台，热更新实测 50ms。接口字段改错编译期就报 |
| WuKongIM SDK | 独立 JS SDK，在 uni-app 侧集成 | 前端后端用同一个 SDK，离线消息、历史、漫游不用自己写 |

---

<a id="内容安全"></a>
## 内容安全

内容安全在这里是独立一层，不在各业务里散着写：文本、图片、视频三条链路都过 `ContentReviewService`，结果落 `tb_review`，人工审核在后台工作台上接着处理。

![内容安全](docs/diagrams/content-safety.png)

---

<a id="推荐引擎"></a>
## 推荐引擎

![推荐引擎 5 层漏斗](docs/diagrams/recommend-funnel.png)

### 双塔训练管线

| 阶段 | 工具 | 产出 |
|------|------|------|
| 特征工程 | Python + MongoDB 用户行为日志 | (user, liked_user, score) 三元组 |
| 模型训练 | PyTorch 权重共享对称双塔 + BPR Loss | 64 维 L2 归一化 User Embedding |
| 权重导出 | `torch.onnx` + numpy 序列 | float32 向量文件 |
| 在线落地 | Qdrant `PUT /points` upsert | pointId = userId，免映射检索 |
| 离线验证 | `--mock` 模式跑管线 | 不依赖外部服务也能验证训练 + 召回 |

训练和权重导出已经能跑通。Qdrant 召回默认是关的（`xfjy.qdrant.enabled=false`），线上候选还是原来的召回链路出。要打开得先把向量灌进去，再灰度一阵子。

![双塔训练管线](docs/diagrams/recommend-twin-tower.png)

---

<a id="系统架构"></a>
## 系统架构

![系统架构](docs/diagrams/architecture.png)

矢量稿在 `docs/diagrams/architecture.svg`，改完导出 2400px 宽的 PNG 覆盖同名文件即可。`architecture.mmd` 只当结构速查用，不出图。

---

<a id="安全设计"></a>
## 安全设计

| 层 | 做法 | 落在哪 |
|----|------|--------|
| 网络边界 | TLS 停在 Nginx，对外只有 8090 | compose 里域服务不映射端口 |
| 域服务鉴权 | `X-Internal-Token`，网关和 server 调用时注入 | `InternalTokenFilter` / `HttpServiceProxyConfig` 注入，6 个域服务用 `XInternalTokenInterceptor` + `@Value` 校验 |
| API 限流 | IP 维度 + 用户维度 | `RateLimitGlobalFilter`（Redis）+ `RiskControlService`（Redis） |
| 用户风控 | Redis 固定窗口 60s 计数 | `RiskControlService` |
| 内容合规 | 文本 DFA + 图视先发后审 | houbb sensitive-word + `tb_review` |
| 恶意注册 | 设备指纹（Redis Set）+ 计数 | 注册防刷 |
| 密码存储 | BCrypt，强度 10 | spring-security-crypto |
| SQL 注入 | MyBatis 参数化 + `@Select` 注解 | MyBatis-Plus |
| XSS | 全局 Result DTO + Jackson Escape | Spring WebMvc |
| 审计日志 | 请求链路 + SLF4J | Filter + logback |

---

<a id="部署运维"></a>
## 部署运维

### 服务端口

`deploy/docker-compose.app.yml` 现在是内网开发/测试编排，所有端口都映射到宿主机并放行防火墙。生产要收敛成只有 8090 对外，域服务只走容器网络。

| 服务 | 端口 | 映射 | 职责 |
|------|------|------|------|
| xfjy-gateway | 8090 | 对外 | 路由 + 限流 + 跨域 + InternalToken 透传 |
| xfjy-sso | 18080 | 内网 | Sa-Token 认证 |
| xfjy-server | 18081 | 内网 | BFF 编排 + 推荐评分 + Qdrant + Neo4j |
| xfjy-user-service | 18090 | 内网 | 用户关系 CRUD |
| xfjy-match-service | 18082 | 内网 | 探花匹配评分 |
| xfjy-circle-service | 18084 | 内网 | 圈子动态发布 → RocketMQ |
| xfjy-video-service | 18085 | 内网 | 小视频流 → RocketMQ |
| xfjy-message-service | 18086 | 内网 | WuKongIM Token + LiveKit Token |
| xfjy-pay-service | 18089 | 内网 | VIP 权益 CRUD |
| xfjy-recommend | 18087 | 内网，不走网关 | MQ 消费者 + 推荐评分 |
| xfjy-elasticsearch | 18088 | 内网，不走网关 | ES HTTP 检索接口 |

### CI/CD

Gitea 28.3.0 + Runner 5.2.4，跑在 192.168.20.133 的 docker 里（`deploy/docker-compose.gitea.yml`），Actions 运行记录永久保留（`RUN_RETENTION_DAYS=0`）。

Runner 5.x 有个坑：每个 job 经 docker socket proxy 建容器，job 结束时默认按 label 强删，`sweep` 不配就是 true。我们部署的是常驻微服务，不关掉会在部署收尾把 11 个后端容器一起删掉，2026-11-02 的 run#119 就是这么挂的。`deploy/gitea/config.yaml` 里已经固定 `container.sweep: false`，改完要 `docker compose -f deploy/docker-compose.gitea.yml restart runner` 才生效。

手动运行（Actions → Run workflow）的输入项全是下拉选择：

- 动作（deploy 工作流）：`deploy` / `build` / `start` / `stop` / `restart` / `status` / `logs`
- 运维（ops 工作流）：`status` / `stop` / `restart` / `logs` / `rollback`
- 目标：`auto`（按变更推断）/ `all` / `backend`（11 个后端）/ `app`（H5）/ `admin` / 单个微服务

| 动作 | 触发 | 行为 |
|------|------|------|
| `deploy` | push master / 手动 | 增量编译 + 构建镜像 + `--build --force-recreate` |
| `build` | 手动 | 只构建镜像，不重启容器 |
| `start / stop / restart` | 手动 | 容器生命周期，自动跳过编译 |
| `status / logs` | 手动 | 运维观测 |
| `rollback`（ops 工作流） | 手动 | 回滚到上一个成功版本或指定 tag |

### 镜像版本

```bash
# 推 tag → 精确版本
git tag v1.2.3 && git push origin --tags
# → xfjy/xfjy-server:v1.2.3

# 推分支 → commit 标记
git push origin master
# → xfjy/xfjy-server:master-a1b2c3d

# 本地部署，自动从 git 取
./manage.sh deploy
# → IMAGE_TAG=v1.2.3 或 commit hash
```

| 输入参数 | 说明 |
|----------|------|
| `target` | 部署/操作目标（auto / all / backend / app / admin / 单个微服务） |
| `skip_ci` | 跳过编译，只用现有产物（start/stop/restart/status/logs 自动跳过） |
| `full_build` | 强制全量 mvn rebuild，忽略增量判定 |

---

<a id="项目结构"></a>
## 项目结构

```
xfjy/
├── pom.xml                     # 根 pom：SB 4.1.1 parent + 版本集中管理
│
├── xfjy-gateway/               # 统一入口（8090）
│   └── filter/                 #   ├── RateLimitGlobalFilter（IP 限流）
│                               #   └── InternalTokenFilter（Token 透传）
│
├── xfjy-server/                # BFF（18081）
│   ├── vector/                 #   ├── QdrantClient（upsert + topK）
│   ├── graph/                  #   └── Neo4jClient（好友度 + 二度推荐）
│   ├── interceptor/            #   └── AdminTokenInterceptor
│   ├── config/
│   │   ├── SaTokenConfigure   #   └── SSO 鉴权过滤链
│   │   └── HttpServiceProxyConfig # @LoadBalanced 域服务路由
│   ├── exception/              #   ├── GlobalExceptionHandler
│   │   └── BusinessException   #   └── 统一业务异常
│   └── service/
│       ├── SensitiveWordService    # sensitive-word DFA 引擎
│       ├── ContentReviewService    # 文本 / 图片 / 视频审核入口
│       ├── RiskControlService      # Redis 用户级风控
│       ├── TodayBestService        # 推荐混合评分（5 层漏斗）
│       └── AdminService            # BCrypt 密码鉴权
│
├── xfjy-sso/                   # Sa-Token + Redis 认证（18080）
├── xfjy-user-service/          # 用户关系（18090）
├── xfjy-match-service/         # 探花匹配（18082）
├── xfjy-circle-service/        # 圈子动态 + RocketMQ（18084）
├── xfjy-video-service/         # 小视频 + RocketMQ（18085）
├── xfjy-message-service/       # WuKongIM + LiveKit（18086）
├── xfjy-pay-service/           # VIP 权益（18089）
├── xfjy-recommend/             # RocketMQ 评分消费者
├── xfjy-elasticsearch/         # ES Geo 检索 API
│
├── xfjy-api/                   # @HttpExchange 契约模块（POJO + 接口）
│
├── xfjy-admin/                 # Vue3 + Element Plus + ECharts
├── xfjy-app/                   # uni-app + Vue 3.5（13 端）
│
├── deploy/
│   ├── docker-compose.app.yml  # 11 个 Java 容器
│   ├── docker-compose.web.yml  # 前端 2 容器
│   ├── docker-compose.middleware.yml  # 12 个中间件容器
│   ├── docker/Dockerfile.java  # 多阶段镜像构建
│   ├── ci/
│   │   ├── stage-artifacts.sh     # 增量编译脚本
│   │   └── settings.xml           # 阿里云 Maven 镜像
│   ├── manage.sh                  # 本地一键部署
│   ├── mysql/init/                # MySQL 初始化脚本（容器 initdb 自动跑）
│   └── .env.example               # 环境变量模板
│
├── docs/                       # 需求文档 · 图标规范 · 归档
│   ├── 需求文档/               # 00 导航 + 01 需求 + 02 约束 + 03 规范 + PRD
│   ├── archive/                # 已失效文档
│   ├── icon-icons.md           # 图标规范
│   └── logo/
│
└── scripts/
    ├── init-kefu.sql           # 客服系统建表
    ├── init-push.sql           # 推送相关表
    └── sync-agent-rules.ps1    # 规则文件同步校验
```

---

<a id="环境变量"></a>
## 环境变量

### 中间件

| 变量 | 说明 | 组件 |
|------|------|------|
| `NACOS_SERVER_ADDR / USERNAME / PASSWORD` | 服务注册 | Nacos 3.x |
| `MYSQL_*` | 关系库 | MySQL 8.0 |
| `REDIS_*` | 缓存 + 会话 + 限流 | Redis 7.x |
| `MONGODB_URI` | 文档库 | MongoDB 7 |
| `ELASTICSEARCH_URIS` | 地理检索 | Elasticsearch 9.x |
| `ROCKETMQ_NAME_SERVER` | 消息队列 | RocketMQ 5.x |
| `MINIO_*` | 对象存储 | MinIO |
| `QDRANT_HOST / PORT / COLLECTION / ENABLED` | 向量检索 | Qdrant |
| `NEO4J_BOLT_URI / USERNAME / PASSWORD / ENABLED` | 图数据库 | Neo4j 5.x |
| `NEO4J_PASSWORD` | 管理端页面密码 | Neo4j |

### 业务服务

| 变量 | 说明 |
|------|------|
| `WUKONGIM_WS_URL` | WuKongIM WebSocket（`ws://192.168.20.133:5200/ws`） |
| `LIVEKIT_URL` | LiveKit WebSocket（`ws://192.168.20.133:7880`） |
| `LIVEKIT_API_KEY / SECRET` | LiveKit Token 签发凭证 |
| `INTERNAL_AUTH_TOKENS` | 域服务鉴权 Token，逗号分隔多组 |
| `QDRANT_ENABLED` | P2 向量开关，默认 false，不可用时降级 |
| `NEO4J_ENABLED` | P2 图推荐开关，默认 false，不可用时降级 |
| `IMAGE_TAG` | 语义版本标签，CI / Git Tag 解析 |

### 第三方能力

| 变量 | 说明 | 没配会怎样 |
|------|------|------------|
| `SMS_ACCESS_KEY_ID / SECRET` | 阿里云短信 | 注册走模拟验证码 |
| `BAIDU_FACE_API_KEY / SECRET_KEY` | 百度 AI 人脸实名 | 跳过实名认证 |

---

<a id="快速启动"></a>
## 快速启动

```bash
# 1. 克隆
git clone https://gitee.com/itxinfei/xfjy.git && cd xfjy

# 2. MySQL 初始化（容器首次启动会自动执行；手动导入按文件名顺序）
mysql -u root -p --default-character-set=utf8mb4 -e "CREATE DATABASE IF NOT EXISTS xfjy DEFAULT CHARSET utf8mb4;"
for f in deploy/mysql/init/*.sql; do mysql -u root -p --default-character-set=utf8mb4 xfjy < "$f"; done

# 3. 后端一键部署（自动解析 git tag）
bash deploy/manage.sh deploy all

# 4. 前端
cd xfjy-admin && npm run dev    # 管理后台 http://localhost:5174
cd xfjy-app && npm run dev:h5   # C 端 H5 http://localhost:5173
```

### 开发工具链

| 工具 | 用途 |
|------|------|
| IntelliJ IDEA 2025 | Java 开发，Lombok 插件必装 |
| HBuilderX | uni-app 开发 + 多端预览 |
| VS Code | Vue3 / 管理后台 |
| DataGrip / DBeaver | 数据库建模 |
| Postman / Apifox | 接口调试 |
| Python + PyTorch（`xfjy-recsys/train`） | 双塔模型训练 |
| Docker Desktop | 中间件编排 |

---

<a id="监控与可观测"></a>
## 监控与可观测

| 端点 | 路径 | 说明 |
|------|------|------|
| 健康检查 | `GET /actuator/health` | 数据源、Redis 等组件状态（`show-details: when-authorized`） |
| 应用信息 | `GET /actuator/info` | 构建时间、Git commit、版本 |
| 运行时指标 | `GET /actuator/metrics` | JVM + HTTP + 连接池 + 自定义业务指标 |

`management.endpoints.web.exposure.include` 目前只有 `health,info,metrics`，没暴露 Prometheus 端点，也没引 `micrometer-registry-prometheus`。

```bash
curl http://127.0.0.1:18081/actuator/health
# {"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"},...}}
```

---

<a id="文档"></a>
## 文档

- [需求文档导航](docs/需求文档/00-需求文档导航总纲.md)：索引和冲突解决记录
- [需求文档](docs/需求文档/01-需求文档.md)：产品功能
- [技术约束](docs/需求文档/02-技术约束.md)：架构规则和现状违反清单
- [AGENTS.md](AGENTS.md)：所有 AI 工具的规则真源
- 数据库脚本在 `deploy/mysql/init/`，全量表结构加初始数据，容器首次启动自动执行

原 `docs/功能就绪矩阵.md` 已归档到 `docs/archive/23-功能就绪矩阵.md`，`docs/技术栈架构全景.html` 没有替代文件。

---

<a id="贡献指南"></a>
## 贡献指南

1. Fork 后从 `feat/your-feature` 起分支
2. 提交信息 `type: 简述`，type 用 feat / fix / refactor / docs / test / chore / perf
3. 密钥不入库，统一 `.env` 外置
4. 改 `xfjy-api` 要 `mvn -am` 连带构建
5. 新增域服务接口，同步 `X-Internal-Token` 透传
6. 数据库表变更走对应域服务，不要跨域写表
7. Qdrant / Neo4j 的调用必须有 `enabled: false` 降级开关

完整流程见 [CONTRIBUTING.md](CONTRIBUTING.md)，行为准则见 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。安全漏洞按 [SECURITY.md](SECURITY.md) 私下报，别开公开 Issue。

---

<a id="路线图"></a>
## 路线图

| 阶段 | 状态 | 内容 |
|------|------|------|
| P1 核心社交闭环 | 已完成 | 探花匹配 · 圈子动态 · 短视频 · 即时通讯 · VIP 权益 · 内容审核 |
| P2 推荐增强 | 进行中 | Qdrant 向量召回 + Neo4j 二度好友，开关可控，不可用时自动降级 |
| P3 实时音视频 | 未开始 | LiveKit SFU 已就位，1v1 通话接口还没接 |
| P4 运营增长 | 未开始 | 活动中心 · 裂变拉新 · 看板增强 · 小程序上架 |

---

<a id="商业支持"></a>
## 商业支持

代码是 Apache-2.0，谁都能拿去商用，所以这里不卖授权，卖的是落地。需要什么走 Issue 或邮件。

| 事项 | 大概包括 |
|------|----------|
| 部署协助 | 12 个中间件容器和 11 个后端服务起来；短信、人脸实名、WuKongIM、LiveKit 这些第三方能力打通；容器访问宿主 IP 的 hairpin、防火墙问题排掉 |
| 二次开发 | 匹配评分、召回链路、审核规则和词库、VIP 权益和支付流程，按模块谈 |
| 私有化交付 | 内网或离线环境部署，中间件国产化替代评估，个人信息保护和内容审核的合规改造建议 |

邮件：747011882@qq.com，Issue 请打 `support` 标签。

---

## License

[Apache-2.0](LICENSE)。归属声明见 [NOTICE](NOTICE)，第三方组件清单见 [THIRD-PARTY-NOTICES](THIRD-PARTY-NOTICES.md)。

---

<div align="center">
<sub>© 2026 itxinfei · Spring Boot 4.1.1 + JDK 25 + Vue 3.5</sub>
</div>
