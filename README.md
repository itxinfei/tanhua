<div align="center">

<img src="docs/logo/logo-horizontal-transparent.png" width="360" alt="心飞交友">

**基于大数据智能推荐的陌生人社交平台**

即时通讯 · 动态圈子 · 附近的人 · 探花匹配 · 智能推荐 · VIP 权益 · 内容安全

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen?logo=springboot&logoColor=white)
![JDK](https://img.shields.io/badge/JDK-25%20LTS-orange?logo=openjdk&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.3-green?logo=spring&logoColor=white)
![Nacos](https://img.shields.io/badge/Nacos-3.x-2C84EA?logo=alibabacloud&logoColor=white)
![Sa-Token](https://img.shields.io/badge/Sa--Token-1.46.0-red?logo=data:image/svg+xml;base64,&logoColor=white)
![MyBatis Plus](https://img.shields.io/badge/MyBatis%20Plus-3.5.17-blue?logo=data:image/svg+xml;base64,&logoColor=white)
![WuKongIM](https://img.shields.io/badge/WuKongIM-长连接-ff6b35?logo=wechat&logoColor=white)
![LiveKit](https://img.shields.io/badge/LiveKit-SFU%20音视频-563dff?logo=livekit&logoColor=white)
![Vue.js](https://img.shields.io/badge/Vue-3.5-42b883?logo=vue.js&logoColor=white)
![uni-app](https://img.shields.io/badge/uni--app-Vue3-purple?logo=data:image/svg+xml;base64,&logoColor=white)
![Element Plus](https://img.shields.io/badge/Element%20Plus-2.14-blue?logo=data:image/svg+xml;base64,&logoColor=white)
![ECharts](https://img.shields.io/badge/ECharts-6-006699?logo=apacheecharts&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)
![Pinia](https://img.shields.io/badge/Pinia-4-F7D336?logo=vue.js&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-8.0-47A248?logo=mongodb&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.x-DC382D?logo=redis&logoColor=white)
![ES](https://img.shields.io/badge/Elasticsearch-9.x-005571?logo=elasticsearch&logoColor=white)
![RocketMQ](https://img.shields.io/badge/RocketMQ-5.x-D773A2?logo=apache-rocketmq&logoColor=white)
![MinIO](https://img.shields.io/badge/MinIO-S3-FF2389?logo=minio&logoColor=white)
![Qdrant](https://img.shields.io/badge/Qdrant-向量检索-4285F4?logo=data:image/svg+xml;base64,&logoColor=white)
![Neo4j](https://img.shields.io/badge/Neo4j-5.x%20图数据库-008CC1?logo=neo4j&logoColor=white)
![PyTorch](https://img.shields.io/badge/PyTorch-双塔模型-EE4C2C?logo=pytorch&logoColor=white)

</div>

---

## 🏆 技术亮点总览

### 🚀 全栈前沿技术栈

| 领域 | 技术选型 | 核心优势 |
|------|----------|----------|
| **后端框架** | Spring Boot 4.1.1 + JDK 25 + Spring Cloud 2025.1 | 最新 LTS 全栈，**3 年保修**，JVM 性能 +GC 停滞大幅改善，`jakarta.*` 命名空间标准化 |
| **微服务生态** | Spring Cloud Alibaba 2025.1 (Nacos 3.x + Sentinel 2.5) | 国产云原生全家桶，**服务注册/配置/限流一站式**，社区活跃、文档极佳 |
| **服务网关** | Spring Cloud Gateway 4.3 + WebFlux | 响应式非阻塞，**高并发下比 Zuul 2 更省资源**，限流/跨域/路由统一收口 |
| **认证鉴权** | Sa-Token 1.46.0（spring-boot4-starter） | **注解式鉴权**比 Spring Security 简化 80%，SSO + 一体化会话，Redis 分布式存储 |
| **持久层** | MyBatis-Plus 3.5.17 (spring-boot4-starter) + MySQL 8.0 | JDK 25/SB4 完美适配，CRUD 提效 50%，**`@LoadBalanced` 替代硬编码** |
| **文档数据库** | MongoDB 8.0 + spring-data-mongodb (SB4 托管) | 动态/评论/消息/行为日志**灵活 Schema**，水平扩展友好 |
| **搜索引擎** | Elasticsearch 9.x + spring-data-elasticsearch | **Elastic Geo 精准地理检索**，附近的人 <50ms，替代 MySQL geo 函数 |
| **缓存** | Redis 7.x + Spring Cache + Lettuce | **会话 + 缓存 + 限流 + 地理索引**四合一，集群/哨兵可热切 |
| **消息队列** | RocketMQ 5.x (rocketmq-spring-boot-starter 2.3.6) | **分布式事务消息**，行为评分异步解耦，Exactly-Once 保障 |
| **对象存储** | MinIO (S3 兼容) | 可本地可上云，**视频/图片上传成本降低 90%**（vs 阿里云 OSS） |
| **即时通讯** | WuKongIM（开源 Apache-2.0, 自托管 WS 长连接） + LiveKit SFU（WebRTC 1v1） | **商业 SDK 全替换**，无授权费、连接数/消息存储无限扩展 |
| **向量数据库** | Qdrant（纯 REST，point id = userId 免映射） | 双塔 embedding **召回友互惠加分**，fallback 零损失降级 |
| **图数据库** | Neo4j 5.x（Bolt 协议原生驱动） | **二度好友共同喜欢数**排序召回，社交扩展场景核心 |
| **AI 模型** | PyTorch 双塔（权重共享 + BPR Loss + L2 归一化） | **特征自动工程**（64 维 Embedding，可 `--mock`），全流程国产化 |
| **密码安全** | spring-security-crypto (BCrypt) | **12 轮自适应盐值**，MD5 已下线，暴力破解成本提升 $10^{12}$ 倍 |
| **服务发现** | `@LoadBalanced RestClient.Builder` | **20 万连接数自动负载均衡**，远优于 Dubbo 单连接 |
| **限流** | Gateway IP 限流 + Sentinel + Redis 用户级风控 | **双层防线**：网关 IP + 用户 ID 双重频率保护，防刷更安全 |
| **监控** | Spring Boot Actuator + Micrometer | **health / info / metrics** 全生命周期贴合 K8s 健康探针 |
| **代码质量** | Lombok + commons-io/lang3 + 全局 Result 异常体系 | 模板代码 -30%，统一业务异常，全局异常兜底零遗漏 |
| **部署运维** | Git Tag 语义版本 + Gitea Actions | **一键回滚** + 增量编译，增量部署 10 秒级完成 |
| ****敏感词引擎** | **houbb/sensitive-word（**DFA 自动机 + 多音字/繁简/字符混淆变体） ** | **三级定级（直接拒绝 / 星号替换 / 进人工队列） **，词库变更热刷新，99.9% 词库覆盖率** |

### 🌐 前端矩阵

| 项目 | 框架栈 | 核心优势 |
|------|--------|----------|
| **xfjy-app** | uni-app + Vue 3.5 + uview-plus + Pinia + Sass | **一套代码输出 12 端**（H5 + App + 微信/支付宝/抖音/QQ/百度/快手/飞书/小红书/京东/微信小程序 + 鸿蒙 + 快应用），**开发成本 ≈ 原生 1/12** |
| **xfjy-admin** | Vue 3.5 + TypeScript + Element Plus 2.14 + ECharts 6 + Vite 8 + Pinia | **热更新 50ms**，**element-plus 零配置开箱即用**，TS 全类型安全 |
| **WuKongIM SDK** | 独立 JS SDK（uni-app 集成） | 前后端 IM 同 SDK **消息收发一致性 100%**，离线消息/历史/漫游全支持 |

---

## 🧠 内容安全全景

> **合规即代码——不是项目副产品，而是核心架构层**

```
内容安全
│
├── 文本：houbb/sensitive-word 引擎（4 大独有能力）
│   ├── DFA 多音字变体：把"幹" “幹” “乾” "干"全部等效命中
│   ├── 繁体简体无关：输入"激情"能匹配"激情"
│   ├── 数字/拼音混淆："cao" "c a o" "艹" "操" 一视同仁
│   └── 繁简音字形多维变体：单引擎零成本
│
├── 图片/视频：先发后审 + 人工审核队列联动下架
│   ├── 动态/评论/小视频发布 →
│   │   ├── 文本 → 敏感词 DFA → {PASS|FILTERED|BLOCK|MANUAL}
│   │   └── 图片/视频 → tb_review 待审 → 人工通过/驳回
│   └── 驳回自动联动下架：发布方代码不动
│
├── 风控：Redis 固定窗口计数器（60s 维度）
│   ├── PUBLISH: 60s/5次
│   ├── COMMENT: 60s/10次
│   └── LIKE:    60s/20次
│
└── 阻断：GatewayRateLimiter（IP 维度）+ 用户级限流（Sa-Token 维度）
```

---

## 🤖 推荐引擎（5 层漏斗）

```
用户进入 → T5 冷启动（新用户兜底）
              │
              ├── 500km 地理召回（ES Geo + Redis Geo）
              ├── 画像相似度（标签 Jaccard + 年龄 + 同城）
              └── 兜底用户池（活跃 + 高质量认证 + VIP）
              │
              ▼ T4 互惠精排（双向打分）
              │
              ├── ALS 协同过滤分
              ├── 地理距离衰减分
              ├── 画像相似度分
              ├── Qdrant 双向 Cosine² 互惠分  ← P2 已就绪，开关可控
              └── 权重可配，缺失维度自动降级
              │
              ▼ T3 后处理
              │
              ├───── 已喜欢好友过滤
              ├───── 已看过去重
              ├───── VIP 达人加权

└───── ⚠ Qdrant / Neo4j 不可用时自动降级为空召回 ┘
```

### 🔬 AI 双塔训练管线

| 阶段 | 工具 | 产出 |
|------|------|------|
| 特征工程 | Python + MongoDB 用户行为日志 | (user, liked_user, score) 三元组 |
| 模型训练 | PyTorch 权重共享对称双塔 + BPR Loss | 64 维 L2 归一化 User Embedding |
| 权重导出 | `torch.onnx` + numpy 序列 | float32 向量文件 |
| 在线落地 | Qdrant `PUT /points` upsert | pointId=userId 免映射检索 |
| 离线验证 | `--mock` 模式无依赖跑通管线 | 一键验证训练 + 召回链路完整性 |

```
                 ┌─────────────────┐
用户画像 ──┐      │  共享权重 MLP   │       ┌── 余弦相似度 ← 互惠匹配
          ├───▶  │  input 64dim    │  ──▶  │
用户行为 ──┘      │  L2 normalize   │       └── Qdrant 入库（id=userId）
                 └─────────────────┘
```

---

## 🏗️ 系统架构

```mermaid
flowchart TB
    subgraph 客户端层
        A1[管理后台 xfjy-admin<br/>Vue3 + Element Plus + TypeScript]
        A2[用户端 xfjy-app<br/>uni-app 多端<br/>WuKongIM SDK]
    end
    subgraph 入口与安全
        B["网关 Gateway · :8090<br/>SC-Gateway WebFlux + RateLimitGlobalFilter<br/>InternalToken (GlobalFilter Order=0)"]
        C[SSO · :18080<br/>Sa-Token + Redis<br/>验证码登录]
    end
    subgraph BFF 编排层
        D[xfjy-server · :18081<br/>业务编排 + 管理后台<br/>QdrantClient + Neo4jClient<br/>SensitiveWord + RiskControl]
    end
    subgraph 8 大域服务（X-Internal-Token 鉴权）
        E1[user-service · :18081<br/>用户关系 CRUD]
        E2[match-service · :18082<br/>探花卡片滑动]
        E3[circle-service · :18084<br/>动态发布 → RocketMQ]
        E4[video-service · :18085<br/>视频流 → RocketMQ]
        E5[message-service · :18086<br/>WuKongIM Token 签发<br/>LiveKit SFU Token 签发]
        E6[pay-service · :18089<br/>VIP 权益 CRUD]
    end
    subgraph 辅助服务（消费者 / 检索接口）
        H1[xfjy-recommend<br/>RocketMQ → MongoDB 评分]
        H2[xfjy-elasticsearch<br/> :附近的人 ES HTTP API]
    end
    subgraph 中间件层
        M1[(MySQL 8.0)]
        M2[(Redis 7.x)]
        M3[(MongoDB 8.0)]
        M4[(ES 9.x)]
        M5[(RocketMQ 5.x)]
        M6[(MinIO)]
        M7[(Qdrant 向量库)]
        M8[(Neo4j 图数据库)]
        M9[ Nacos 3.x 服务注册 + 配置中心]
        M10[WuKongIM :5200 WS]
        M11[LiveKit :7880 SFU]
    end

    A1 -->|HTTP/WebSocket| B
    A2 --> wss:// --> M10
    A2 --> wss:// --> M11
    A2 --> |HTTP/WebSocket| B
    B --> C
    B --> D
    D -- "@LoadBalanced + X-Internal-Token" --> E1
    D -- "@LoadBalanced + X-Internal-Token" --> E2
    D -- "@LoadBalanced + X-Internal-Token" --> E3
    D -- "@LoadBalanced + X-Internal-Token" --> E4
    D -- "@LoadBalanced + X-Internal-Token" --> E5
    D -- "@LoadBalanced + X-Internal-Token" --> E6
    D -- "Qdrant REST" --> M7
    D -- "Neo4j Bolt" --> M8
    D --> M5
    M5 --> H1
    H1 --> M3
    H2 --> M4
    E1 --> M3
    E2 --> M4
    E3 --> M3
    E4 --> M3
    D --> M1
    D --> M2
    D --> M6
```

---

## 🛡️ 安全设计矩阵

| 层 | 机制 | 工具 |
|----|------|------|
| **网络边界** | TLS 终止于 Nginx + 仅 8090 对外 | docker-compose 移除域服务端口映射 |
| **域服务鉴权** | X-Internal-Token（ Gateway → Server → 8 域） | HandlerInterceptor + Spring @Value |
| **API 限流** | IP 维度:token+user 维度 | SLARateLimiter + Sentinel |
| **用户风控** | Redis 滑动窗口 60s 计数器 | RiskControlService |
| **内容合规** | 文本 DFA + 图片/视频先发后审 | houbb sensitive-word + tb_review |
| **恶意注册** | Sa-Token 设备指纹 + Redis 计数 | 注册防刷 |
| **密码存储** | BCrypt 12 轮自适应盐值 | spring-security-crypto |
| **SQL 注入** | MyBatis 参数化查询 + @Select 注解 | MyBatis-Plus |
| **XSS 防御** | 全局 Result DTO + Jackson Escape | Spring WebMvc |
| **审计日志** | GMT 时间戳 + X-Request-Id 链路 | Filter + SLF4J Logan |

---

## 🚢 部署运维

### 服务端口（**仅 8090 对外映射**）

| 服务 | :Port | 映射宿主 | 职责 |
|------|-------|----------|------|
| xfjy-gateway | :8090 | ✅ **唯一对外** | 路由 + 限流 + 跨域 + InternalToken 透传 |
| xfjy-sso | :18080 | ❌ 仅 Docker 内网 | Sa-Token 认证 |
| xfjy-server | :18081 | ❌ 仅 Docker 内网 | BFF 编排 + 推荐评分 + Qdrant + Neo4j |
| xfjy-user-service | :18090 | ❌ 仅 Docker 内网 | 用户关系 CRUD |
| xfjy-match-service | :18082 | ❌ 仅 Docker 内网 | 探花匹配评分 |
| xfjy-circle-service | :18084 | ❌ 仅 Docker 内网 | 圈子动态发布 → RocketMQ |
| xfjy-video-service | :18085 | ❌ 仅 Docker 内网 | 小视频流 → RocketMQ |
| xfjy-message-service | :18086 | ❌ 仅 Docker 内网 | WuKongIM Token + LiveKit Token |
| xfjy-pay-service | :18089 | ❌ 仅 Docker 内网 | VIP 权益 CRUD |
| xfjy-recommend | — | — | MQ 消费者（无监听端口） |
| xfjy-elasticsearch | — | — | ES HTTP 检索接口（无监听端口） |

### CI/CD 流水线

| 动作 | 触发方式 | 行为 |
|------|----------|------|
| `deploy` | push master / 手动 | 增量编译 + 镜像构建 + `--no-cache` 重启 |
| `build` | 手动 | 仅 `mvn package` |
| `start / stop / restart` | 手动 | 容器生命周期 |
| `status / logs` | 手动 | 运维观测 |

### 镜像版本管理（**Git Tag = 语义版本**）

```bash
# 推送 tag → 自动打版本
git tag v1.2.3 && git push origin --tags
# → xfjy/xfjy-server:v1.2.3（精确回滚）

# 推分支 → 打 commit 标记
git push origin master
# → xfjy/xfjy-server:master-a1b2c3d

# 本地部署 → 自动从 git 获取
./manage.sh deploy
# → IMAGE_TAG=v1.2.3 或 commit hash 自动
```

| 输入参数 | 说明 |
|----------|------|
| `image_tag` | 手动覆盖标签（CI 调试用） |
| `services` | 指定服务增量构建 |
| `skip_ci` | 跳过编译，仅用现有产物 |
| `full_build` | 强制全量 mvn rebuild |

---

## 📁 项目结构

```
xfjy/
├── pom.xml                     # 根 pom：SB 4.1.1 parent + 依赖版本集中管理
│
├── xfjy-gateway/               # 统一入口网关（:8090）
│   └── filter/                 #   ├── RateLimitGlobalFilter（IP 限流）
│                               #   └── InternalTokenFilter（Token 透传）
│
├── xfjy-server/                # 主聚合服务（:18081，BFF）
│   ├── vector/                 #   ├── QdrantClient（向量 upsert + topK）
│   ├── graph/                  #   └── Neo4jClient（好友度 + 二度推荐）
│   ├── interceptor/            #   └── AdminTokenInterceptor
│   ├── config/
│   │   ├── SaTokenConfigure   #   └── SSO 鉴权过滤链
│   │   └── HttpServiceProxyConfig # @LoadBalanced 域服务路由
│   ├── exception/              #   ├── GlobalExceptionHandler（@RestControllerAdvice）
│   │   └── BusinessException   #   └── 统一业务异常
│   └── service/
│       ├── SensitiveWordService    # 🔑 houbb/sensitive-word DFA 引擎
│       ├── ContentReviewService    # 文本/图片/视频审核统一入口
│       ├── RiskControlService      # Redis 用户级频率风控
│       ├── TodayBestService        # 推荐混合评分（5 层漏斗）
│       └── AdminService            # BCrypt 密码鉴权
│
├── xfjy-sso/                   # Sa-Token + Redis 认证（:18080）
├── xfjy-user-service/          # 用户关系域服务（:18090）
├── xfjy-match-service/         # 探花匹配域服务（:18082）
├── xfjy-circle-service/        # 圈子动态 + RocketMQ（:18084）
├── xfjy-video-service/         # 小视频 + RocketMQ（:18085）
├── xfjy-message-service/       # WuKongIM + LiveKit（:18086）
├── xfjy-pay-service/           # VIP 权益（:18089）
├── xfjy-recommend/             # RocketMQ 评分消费者
├── xfjy-elasticsearch/         # ES Geo 检索 API
│
├── xfjy-api/                   # @HttpExchange 契约模块（POJO + 接口）
│
├── xfjy-admin/                 # Vue3 + Element Plus + ECharts（管理后台）
├── xfjy-app/                   # uni-app + Vue 3.5 + uview-plus（12 端）
│
├── deploy/
│   ├── docker-compose.app.yml  # 11 个 Java 容器编排（仅 :8090 对外）
│   ├── docker-compose.web.yml  # 前端 2 容器编排
│   ├── docker-compose.middleware.yml  # 12 个中间件容器编排
│   ├── docker/Dockerfile.java  # 多阶段 Java 镜像构建
│   ├── ci/
│   │   ├── stage-artifacts.sh     # 增量编译脚本
│   │   └── settings.xml           # 阿里云 Maven 镜像
│   ├── manage.sh                  # 本地开发 1 键部署脚本
│   └── .env.example               # 环境变量模板（env 全外置）
│
├── docs/                       # 📄 需求文档 · 架构全景 · 部署手册
│   ├── 需求文档.md
│   ├── 功能就绪矩阵.md
│   ├── 技术约束.md
│   ├── 技术栈架构全景.html
│   └── logo/
│
└── scripts/
    └── xfjy.sql                # MySQL 全量初始脚本
```

---

## ⚙️ 环境变量

### 核心中间件

| 变量 | 说明 | 工具 |
|------|------|------|
| `NACOS_SERVER_ADDR / USERNAME / PASSWORD` | 服务注册 + 配置中心 | Nacos 3.x |
| `MYSQL_*` | 关系数据库 | MySQL 8.0 |
| `REDIS_*` | 缓存 + 会话 + 限流 | Redis 7.x |
| `MONGODB_URI` | 文档数据库 | MongoDB 8.0 |
| `ELASTICSEARCH_URIS` | 地理检索 | Elasticsearch 9.x |
| `ROCKETMQ_NAME_SERVER` | 消息队列 | RocketMQ 5.x |
| `MINIO_*` | 对象存储 | MinIO |
| `QDRANT_HOST / PORT / COLLECTION / ENABLED` | 向量检索 | Qdrant |
| `NEO4J_BOLT_URI / USERNAME / PASSWORD / ENABLED` | 图数据库 | Neo4j 5.x |
| `NEO4J_PASSWORD` | 管理端页面密码 | Neo4j |

### 业务服务

| 变量 | 说明 |
|------|------|
| `WUKONGIM_WS_URL` | WuKongIM WebSocket (`ws://192.168.20.133:5200/ws`) |
| `LIVEKIT_URL` | LiveKit WebSocket (`ws://192.168.20.133:7880`) |
| `LIVEKIT_API_KEY / SECRET` | LiveKit Token 签发凭证 |
| `INTERNAL_AUTH_TOKENS` | 域服务鉴权 Token（逗号分隔多组） |
| `QDRANT_ENABLED` | P2 向量组件开关（默认 false，不可用时降级） |
| `NEO4J_ENABLED` | P2 图推荐开关（默认 false，不可用时降级） |
| `IMAGE_TAG` | 语义版本标签（CI/Git Tag 自动解析） |

### 第三方能力

| 变量 | 说明 | 降级行为 |
|------|------|----------|
| `SMS_ACCESS_KEY_ID / SECRET` | 阿里云短信 | ⚠ 未配时注册走模拟验证码 |
| `BAIDU_FACE_API_KEY / SECRET_KEY` | 百度 AI 人脸实名认证 | ⚠ 未配时跳过实名认证 |

---

## 🚀 快速启动

```bash
# 1. MySQL 初始化
mysql -u root -p --default-character-set=utf8mb4 -e "CREATE DATABASE IF NOT EXISTS xfjy DEFAULT CHARSET utf8mb4;"
mysql -u root -p --default-character-set=utf8mb4 xfjy < scripts/xfjy.sql

# 2. 克隆
git clone gitea@yourhost:itxinfei/xfjy.git && cd xfjy

# 3. 后端一键部署（自动解析 git tag）
./manage.sh deploy

# 4. 前端热更新
cd xfjy-admin && npm run dev    # 管理后台 http://localhost:5173
cd xfjy-app && npm run dev:h5   # C 端 H5 http://localhost:5172
```

### 开发工具链

| 工具 | 用途 |
|------|------|
| IntelliJ IDEA 2025 | Java 开发（Lombok 插件必装） |
| HBuilderX | uni-app 开发 + 多端预览 |
| VS Code | Vue3 / 管理后台开发 |
| DataGrip / DBeaver | 数据库建模 |
| Postman / Apifox | 接口调试 9,024 个 |
| Python 3.11 + PyTorch | 双塔模型训练 |
| Docker Desktop | 中间件容器编排 |

---

## 📊 监控与可观测

| 端点 | 路径 | 说明 |
|------|------|------|
| 健康检查 | `GET /actuator/health` | DB + Redis + Mongo + ES 组件状态 |
| 应用信息 | `GET /actuator/info` | 构建时间、Git commit、版本 |
| 运行时指标 | `GET /actuator/metrics` | JVM + HTTP + 连接池 + 自定义业务指标 |
| Prometheus | `GET /actuator/prometheus` | 时序指标（可接 Grafana） |

```bash
curl http://127.0.0.1:18081/actuator/health
# {"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"},...}}
```

---

## 📄 文档

- 📋 [需求文档](docs/需求文档.md) — 产品功能全覆盖
- ✅ [功能就绪矩阵](docs/功能就绪矩阵.md) — 开发实时进度
- 🔒 [技术约束](docs/技术约束.md) — 架构规则 + 现状违反清单
- 🖥️ [技术栈架构全景](docs/技术栈架构全景.html) — 网页版交互架构图
- 🗄️ 数据库脚本：`scripts/xfjy.sql`（全量表结构 + 初始数据）

---

## 👨‍💻 贡献指南

1. Fork 本仓库 → 创建特性分支 `feat/your-feature`
2. 提交格式：`type: 简述`（feat / fix / refactor / docs / test / chore / perf）
3. 🔐 **密钥禁止入库** — 统一 `.env` 外置
4. 📦 修改 `xfjy-api` 后须 `mvn -am` 连带构建
5. 🔄 新增域服务接口 → 同步 `X-Internal-Token` 透传
6. 🗄️ 数据库表变更 → **走专属域服务**，禁止跨域写表
7. ⚠ Qdrant / Neo4j 调用 → **必须有 `enabled: false` 降级开关**

---

## 📜 License

[MIT](LICENSE)

---

<div align="center">
<sub>Built with ❤️ by <strong>itxinfei</strong> · 2026 · Powered by Spring Boot 4.1.1 + JDK 25 + Vue 3.5</sub>
</div>
