<div align="center">

<img src="docs/logo/logo-horizontal-transparent.png" width="360" alt="心飞交友">

**基于大数据智能推荐的陌生人社交平台**

即时通讯 · 动态圈子 · 附近的人 · 探花匹配 · 智能推荐 · VIP 权益

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![JDK](https://img.shields.io/badge/JDK-25-orange)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.3-green)
![Sa-Token](https://img.shields.io/badge/Sa--Token-1.46.0-red)
![MyBatis Plus](https://img.shields.io/badge/MyBatis%20Plus-3.5.17-blue)
![MongoDB](https://img.shields.io/badge/MongoDB-8.0-47A248)
![Redis](https://img.shields.io/badge/Redis-7.x-DC382D)
![RocketMQ](https://img.shields.io/badge/RocketMQ-5.x-D773A2)
![WuKongIM](https://img.shields.io/badge/WuKongIM-长连接-ff6b35)
![LiveKit](https://img.shields.io/badge/LiveKit-音视频-563dff)
![Vue](https://img.shields.io/badge/Vue-3.5-42b883)
![Element Plus](https://img.shields.io/badge/Element%20Plus-2.14-blue)
![uni-app](https://img.shields.io/badge/uni--app-Vue3-purple)
![Qdrant](https://img.shields.io/badge/Qdrant-向量检索-4285F4)
![Neo4j](https://img.shields.io/badge/Neo4j-图推荐-008CC1)
![PyTorch](https://img.shields.io/badge/PyTorch-双塔模型-EE4C2C)
![License](https://img.shields.io/badge/License-MIT-yellow)

</div>

---

## 一、项目简介

心飞交友是一款面向年轻群体的陌生人社交平台，采用 Spring Boot 4.1 + JDK 25 微服务架构，前端覆盖 H5 / 小程序 / App 多端。

### 核心功能

| 模块 | 功能 |
|------|------|
| 📍 附近的人 | Elasticsearch Geo 地理位置搜索，按距离排序 |
| 🤝 探花匹配 | 卡片滑动（喜欢/不喜欢/超级喜欢）+ 陌生人问题破冰 |
| 📰 圈子动态 | 图文动态发布、点赞、评论、时间线 |
| 🎬 小视频 | 视频上传、推荐流、关注作者 |
| 💬 实时聊天 | 集成 WuKongIM（开源自建 IM）+ LiveKit 1v1 音视频，互相喜欢自动成为联系人 |
| 🤖 智能推荐 | 冷启动兜底 + ALS 协同过滤 + 地理衰减 + 画像相似度 + 互惠精排 |
| 💎 VIP 权益 | 权益矩阵驱动：超级喜欢 / 谁喜欢我 / 无限滑动 / 隐身访问 |
| 🛡️ 管理后台 | 内容审核、敏感词过滤、用户管理、数据看板 |

### 推荐引擎亮点

- **T5 冷启动**：新用户无行为数据时，地理召回（500km）+ 画像相似度（标签 Jaccard + 年龄 + 同城）兜底
- **混合评分**：ALS 分数 × 地理距离衰减 × 画像相似度 × 互惠匹配分，权重可配置，缺失维度自动降级
- **T4 互惠精排**：基于双塔 embedding 的 cosine² 双向匹配分，放大"双向高相似"候选
- **向量召回**：Qdrant point id = userId 免映射，互惠匹配补充加分，不可用时自动降级空召回
- **图推荐**：Neo4j Bolt 协议二度好友（共同喜欢数排序），喜欢关系实时同步，不可用时自动降级空召回
- **双塔训练**：Python + PyTorch 权重共享对称双塔，BPR loss，`--mock` 模式无需中间件验证管线

---

## 二、技术栈

### 后端

| 层次 | 技术 | 版本 |
|------|------|------|
| 语言 | JDK | 25 |
| 框架 | Spring Boot | 4.1.1 |
| 微服务 | Spring Cloud + Nacos | 2025.1.3 / 3.x |
| 网关 | Spring Cloud Gateway | 4.3.x |
| 认证鉴权 | Sa-Token | 1.46.0 |
| 持久层-关系 | MyBatis-Plus + mysql-connector-j | 3.5.17 |
| 持久层-文档 | spring-data-mongodb | SB4 托管 |
| 持久层-搜索 | spring-data-elasticsearch | SB4 托管 |
| 缓存 | Spring Cache + Lettuce Redis | SB4 托管 |
| 消息队列 | rocketmq-spring-boot-starter | 2.3.6 |
| 服务间通信 | HTTP Service Clients（@HttpExchange + RestClient） | — |
| 对象存储 | MinIO（S3 兼容） | 8.5.17 |
| 即时通讯 | WuKongIM（开源 Apache-2.0，自托管长连接） + LiveKit SFU（WebRTC 1v1 音视频） | — |
| 向量检索 | Qdrant（纯 REST 接入，64 维 Cosine） | — |
| 图数据库 | Neo4j 5.x（Bolt 协议，二度好友推荐） | — |
| 密码编码 | spring-security-crypto（BCrypt） | SB4 托管 |
| 服务发现 | @LoadBalanced RestClient.Builder（SC LoadBalancer） | — |
| 运行监控 | Spring Boot Actuator | health/info/metrics |
| 工具 | Lombok / commons-io / commons-codec / spring-security-crypto | — |

### AI 训练工程

| 组件 | 技术 | 说明 |
|------|------|------|
| 双塔模型 | PyTorch | 权重共享对称双塔，BPR loss，64 维 L2 归一化输出 |
| 特征工程 | 自研 | tags multi-hot + age 归一化 + sex/city/edu/industry Embedding |
| 数据源 | MongoDB（user_like）+ MySQL（画像） | 行为数据 → (user, liked_user, score) 三元组 |
| 落地 | Qdrant REST upsert | point id = userId，与 Java 侧载荷完全对齐 |

### 前端

| 端 | 技术 | 说明 |
|------|------|------|
| 管理端 | Vue 3.5 + Element Plus 2.14 + ECharts + Pinia + Vite 8 + TypeScript | xfjy-admin |
| 用户端 | uni-app + Vue 3.5 + uview-plus + Pinia + vue-i18n + Sass | xfjy-app（H5 / 小程序 / App） |

---

## 三、系统架构

```mermaid
flowchart TB
    subgraph 客户端层
        A1[管理后台 xfjy-admin<br/>Vue3 + Element Plus]
        A2[用户端 xfjy-app<br/>uni-app 多端]
    end
    subgraph 入口与认证
        B[网关 Gateway · 8090<br/>路由/限流/跨域 + InternalToken透传]
        C[SSO 认证 · 18080<br/>验证码登录 Sa-Token + Redis]
    end
    subgraph 主业务
        D[主聚合服务 xfjy-server · 18081<br/>业务 + 管理后台 + 推荐评分<br/>QdrantClient + Neo4jClient]
    end
    subgraph 拆分服务（8 个，X-Internal-Token 鉴权）
        E1[用户关系 · 18090]
        E2[圈子动态 · 18084]
        E3[小视频 · 18085]
        E4[探花匹配 · 18082]
        E5[消息/IM · 18086<br/>WuKongIM + LiveKit]
        E6[支付/VIP · 18089]
    end
    subgraph 辅助服务（消费者 / 检索接口）
        H1[推荐评分消费者 xfjy-recommend<br/>RocketMQ → MongoDB]
        H2[ES Geo 检索 xfjy-elasticsearch<br/>附近的人 HTTP 接口]
    end
    subgraph 数据层
        F1[(MySQL 8.0<br/>事务数据)]
        F2[(Redis 7.x<br/>缓存/会话/限流)]
        F3[(MongoDB 8.0<br/>文档数据)]
        F4[(Elasticsearch 9.x<br/>Geo 检索)]
        F5[(RocketMQ 5.x<br/>行为消息)]
        F6[(MinIO<br/>对象存储)]
        F7[(Qdrant<br/>向量 embedding)]
        F8[(Neo4j 5.x<br/>好友关系图)]
    end
    A1 --> B
    A2 --> B
    B --> C
    B --> D
    D -- @HttpExchange + X-Internal-Token --> E1
    D -- @HttpExchange + X-Internal-Token --> E2
    D -- @HttpExchange + X-Internal-Token --> E3
    D -- @HttpExchange + X-Internal-Token --> E4
    D -- @HttpExchange + X-Internal-Token --> E5
    D -- @HttpExchange + X-Internal-Token --> E6
    D -- QdrantClient HTTP --> F7
    D -- Neo4jClient Bolt --> F8
    B -- InternalTokenFilter 透传 --> D
    D --> F5
    F5 --> H1
    H1 --> F3
    H2 --> F4
    E1 --> F3
    E2 --> F3
    E3 --> F3
    E4 --> F4
    D --> F1
    D --> F2
    D --> F6
```

### 服务端口

| 服务 | 端口 | 映射宿主 | 职责 |
|------|------|----------|------|
| xfjy-gateway | 8090 | ✅ 唯一对外 | 统一入口（路由/限流/跨域） |
| xfjy-sso | 18080 | ❌ 仅内网 | 单点登录（Sa-Token + Redis） |
| xfjy-server | 18081 | ❌ 仅内网 | 主业务聚合 + 管理后台 |
| xfjy-user-service | 18090 | ❌ 仅内网 | 用户关系 |
| xfjy-match-service | 18082 | ❌ 仅内网 | 探花匹配 |
| xfjy-circle-service | 18084 | ❌ 仅内网 | 圈子动态 |
| xfjy-video-service | 18085 | ❌ 仅内网 | 小视频 |
| xfjy-message-service | 18086 | ❌ 仅内网 | WuKongIM + LiveKit |
| xfjy-pay-service | 18089 | ❌ 仅内网 | 支付/VIP |
| xfjy-recommend | — | — | MQ 消费者 |
| xfjy-elasticsearch | — | — | ES Geo 检索接口 |

> **安全设计**：仅网关 8090 端口对外映射，18081~18090 全部为 Docker 内部端口，外部不可直接访问。
> 域服务启用 `X-Internal-Token` 请求头鉴权，调用方（server/gateway）自动透传 Token。

**数据链路**：用户行为 → xfjy-server → RocketMQ → xfjy-recommend → MongoDB → 推荐接口混合评分 → 前端消费。

**网关路由**：仅做边缘路由（sso + xfjy-server）。域服务由 xfjy-server 经 @HttpExchange + LoadBalancer 内部直连，不经网关。

---

## 四、项目结构

```
xfjy/
├── pom.xml                     # 根 pom：SB 4.1.1 parent + 依赖版本集中管理
├── xfjy-gateway/               # 统一入口网关（8090，Spring Cloud Gateway）
├── xfjy-sso/                   # 认证服务（18080，验证码登录，Sa-Token + Redis）
├── xfjy-server/                # 主业务聚合服务（18081，边缘入口 + 管理后台）
├── xfjy-user-service/          # 用户关系服务（18090，Mongo：好友/喜欢/访客）
├── xfjy-match-service/         # 探花匹配服务（18082，推荐用户/地理位置）
├── xfjy-circle-service/        # 圈子动态服务（18084，动态/评论/时间线）
├── xfjy-video-service/         # 小视频服务（18085，视频/关注作者）
├── xfjy-message-service/       # 消息/IM 服务（18086，WuKongIM 长连接 + LiveKit SFU 音视频）
├── xfjy-pay-service/           # 支付/VIP 权益服务（18089）
├── xfjy-recommend/             # 推荐评分消费者（RocketMQ → MongoDB）
├── xfjy-elasticsearch/         # ES Geo 地理位置：附近的人
├── xfjy-api/                    # 内部 HTTP 接口契约（@HttpExchange）
├── xfjy-admin/                 # 管理后台前端（Vue3 + Element Plus + Vite 8）
├── xfjy-app/                   # 用户端前端（uni-app：H5/小程序/App）
├── docs/                       # 需求文档、数据库脚本、logo
└── scripts/                    # 部署/验证脚本（不入库）
```

---

## 五、快速开始

### 环境要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 25 | 后端编译运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 22+ | 前端构建 |
| Python | 3.11+ | 双塔训练（可选） |
| MySQL | 8.0 | 关系数据 |
| Redis | 7.x | 缓存/会话 |
| MongoDB | 8.0 | 动态/关系/评分 |
| Elasticsearch | 8.x | 地理搜索 |
| RocketMQ | 5.x | 行为消息 |
| Nacos | 3.x | 服务注册/配置 |

> Qdrant / Neo4j 为可选中间件，未部署时推荐引擎自动降级，不阻断主链路。

### 后端启动

```bash
# 1. 初始化数据库（全量脚本：表结构+初始数据，一键导入）
mysql -u root -p --default-character-set=utf8mb4 -e "CREATE DATABASE IF NOT EXISTS xfjy DEFAULT CHARSET utf8mb4;"
mysql -u root -p --default-character-set=utf8mb4 xfjy < scripts/xfjy.sql

# 2. 编译
mvn clean compile

# 3. 启动（按顺序）
# Nacos → MySQL → Redis → MongoDB → ES → RocketMQ
# 然后：sso → server → gateway → 各拆分服务
```

### 前端启动

```bash
# 管理后台
cd xfjy-admin && npm install && npm run dev

# 用户端 H5
cd xfjy-app && npm install && npm run dev:h5
```

---

## 六、环境变量配置

所有敏感配置通过环境变量注入，模板见 [.env.example](.env.example)。

### 基础中间件

| 变量 | 说明 | 默认值 |
|------|------|--------|
| NACOS_SERVER_ADDR | Nacos 地址 | 127.0.0.1:8848 |
| NACOS_USERNAME | Nacos 用户名 | nacos |
| NACOS_PASSWORD | Nacos 密码 | （空） |
| MYSQL_URL | MySQL 连接串 | 127.0.0.1:3306/xfjy |
| MYSQL_PASSWORD | MySQL 密码 | （空） |
| REDIS_HOST | Redis 地址 | 127.0.0.1 |
| REDIS_PASSWORD | Redis 密码 | （空） |
| MONGO_URI | MongoDB 连接串 | 127.0.0.1:27017/xfjy |
| ROCKETMQ_NAME_SERVER | RocketMQ NameServer | 127.0.0.1:9876 |

### 第三方服务

| 变量 | 说明 |
|------|------|
| SMS_ACCESS_KEY_ID / SECRET | 阿里云短信 |
| MINIO_ENDPOINT / ACCESS_KEY_ID / ACCESS_KEY_SECRET / BUCKET / URL_PREFIX | MinIO 对象存储（minio.properties） |
| BAIDU_FACE_API_KEY / SECRET_KEY | 百度 AI 人脸 |
| WUKONGIM_WS_URL / LIVEKIT_URL | WuKongIM WebSocket + LiveKit WebRTC | 自托管 IM/音视频 |
| QDRANT_HOST / QDRANT_PORT / COLLECTION | Qdrant 向量检索 | 互惠匹配增强 |
| QDRANT_ENABLED | Qdrant 开关（默认 false） | 降级控制 |
| NEO4J_BOLT_URI / NEO4J_USERNAME / NEO4J_PASSWORD | Neo4j 图数据库 | 二度好友推荐 |
| NEO4J_ENABLED | Neo4j 开关（默认 false） | 降级控制 |
| INTERNAL_AUTH_TOKENS | 域服务鉴权 Token（逗号分隔） | X-Internal-Token |
| IMAGE_TAG | 镜像语义标签（如 v1.2.3、master-a1b2c3d） | CI 精确回滚 |

> 未配置的第三方服务走占位/模拟模式，开发环境可用。

---

## 七、监控与持续集成

### 运行监控（Actuator）

xfjy-server 集成 Spring Boot Actuator，暴露以下端点（无需认证）：

| 端点 | 说明 |
|------|------|
| `GET /actuator/health` | 健康检查（含 DB/Redis/Mongo 等组件状态） |
| `GET /actuator/info` | 应用信息 |
| `GET /actuator/metrics` | 指标列表及明细 |

```bash
# 健康检查示例
curl http://127.0.0.1:18081/actuator/health
# 预期: {"status":"UP"}
```

### CI / CD 流水线

[.gitea/workflows/deploy.yml](.gitea/workflows/deploy.yml) 定义 Gitea Actions 部署流水线，支持 7 种动作：

| 动作 | 用途 |
|------|------|
| `deploy` | 增量编译 + 构建镜像 + 部署（默认） |
| `build` | 仅编译打包，不部署 |
| `start / stop / restart` | 容器生命周期管理 |
| `status` | 查看容器运行状态 |
| `logs` | 查看容器日志 |

**镜像版本管理**：
- 推送 git tag `v*` → 镜像自动打对应标签（如 `xfjy/xfjy-server:v1.2.3`），可精确回滚
- 推送分支 → 镜像打 `分支名-commitHash`（如 `xfjy/xfjy-server:main-a1b2c3d`）
- 本地 `./manage.sh deploy` → 自动从 git 获取标签/commit，无需手动配置
- 变量 `IMAGE_TAG` 覆盖此行为（CI 手动触发时指定）

**输入参数**（手动触发时）：

| 参数 | 说明 |
|------|------|
| `image_tag` | 镜像标签（覆盖自动解析） |
| `services` | 指定服务（空格分隔，留空自动推断变更范围） |
| `skip_ci` | 跳过编译，仅启停容器 |
| `full_build` | 强制全量重建 |

触发条件：push 到 master 手动运行。

---

## 八、架构亮点（v2.2 更新）

| 维度 | 实现 |
|------|------|
| **服务发现** | `@LoadBalanced RestClient.Builder` 替代 `instances.get(0)` 硬编码，自动负载均衡 |
| **数据所有权** | 8 域服务各司其表，跨表操作通过 PayApi 契约调用，无跨服务双写 |
| **域服务鉴权** | `X-Internal-Token` 请求头鉴权，Gateway → Server → 域服务三级透传 |
| **全局异常** | `@RestControllerAdvice` + `Result<T>` 统一响应体，`BusinessException` 业务错误码 |
| **密码安全** | BCrypt 替代 MD5（含管理员全链路） |
| **端口暴露** | 仅网关 8090 对外，18081~18090 全部内网 Docker 端口 |
| **镜像版本** | Git Tag 语义版本支持精确回滚，分支+commit hash 自动标签 |
| **向量召回** | Qdrant embedding 互惠匹配加分，不可用自动降级空召回 |
| **图推荐** | Neo4j Bolt 协议二度好友查询，不可用自动降级空召回 |
| **配置外置** | 中间件密码/env 全部 `.env` 注入，`.env.example` 模板化 |

---

## 九、文档

- 📄 [需求文档](docs/需求文档.md)
- 🗄️ 数据库脚本：本地 `scripts/xfjy.sql`（全量表结构 + 初始数据，一键导入，不入库）

---

## 十、贡献指南

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feat/your-feature`
3. 提交改动：`git commit -m "feat: 简述"`
4. 推送分支：`git push origin feat/your-feature`
5. 提交 Pull Request

### 开发规范

- commit message 格式：`type: 简述`（feat / fix / refactor / docs / test / chore / perf）
- 密钥/凭证禁止入库，统一环境变量外置（`.env.example` 为模板）
- 改动 `xfjy-api` 后须 `mvn -am` 连带构建
- 新增域服务接口时，同步补充 `X-Internal-Token` 透传（参考 `HttpServiceProxyConfig` `defaultHeader`）
- 数据库表变更优先走专属域服务，禁止跨服务直接写其他域的表
- Qdrant / Neo4j 调用必须有 `enabled: false` 降级开关

---

## 十一、License

[MIT](LICENSE)

---

<div align="center">
<sub>Built with ❤️ by itxinfei</sub>
</div>