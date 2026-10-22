<div align="center">
  <h1>🌸 心飞交友（xfjy）</h1>
  <strong>基于大数据智能推荐的陌生人社交平台</strong>

  <em>即时通讯 · 动态圈子 · 附近的人 · 探花匹配 · 智能推荐</em>

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![JDK](https://img.shields.io/badge/JDK-25-orange)
![MyBatis Plus](https://img.shields.io/badge/MyBatis%20Plus-3.5.17-blue)
![Sa-Token](https://img.shields.io/badge/Sa--Token-1.46.0-red)
![Spark](https://img.shields.io/badge/Spark%20MLlib-4.2.0-yellow)
![MongoDB](https://img.shields.io/badge/MongoDB-SB4%20Starter-green)
![RocketMQ](https://img.shields.io/badge/RocketMQ-2.3.6-red)
![Vue](https://img.shields.io/badge/Vue-3.5-42b883)
![Element Plus](https://img.shields.io/badge/Element%20Plus-2.14-blue)
![uni-app](https://img.shields.io/badge/uni--app-Vue3-purple)
![Vite](https://img.shields.io/badge/Vite-8%20%2F%205-646cff)
</div>

---

## 一、项目简介

心飞交友是一款面向年轻群体的陌生人社交平台，核心功能：

- 📍 **附近的人**：Elasticsearch Geo 地理位置搜索
- 🤝 **探花匹配**：卡片滑动 + 陌生人问题破冰
- 📰 **圈子动态**：图文动态、点赞、评论
- 🎬 **小视频**：上传、推荐、播放
- 💬 **实时聊天**：集成环信 IM
- 🤖 **智能推荐**：Spark MLlib 协同过滤离线计算 + RocketMQ 实时评分

---

## 二、技术栈

### 后端

| 层次 | 技术 | 版本 |
|------|------|------|
| 语言 | JDK | 25 |
| 框架 | Spring Boot | 4.1.1 |
| 认证鉴权 | Sa-Token（sa-token-spring-boot4-starter） | 1.46.0 |
| 持久层-关系 | MyBatis-Plus + mybatis-plus-jsqlparser + mysql-connector-j | 3.5.17 |
| 持久层-文档 | spring-data-mongodb | SB4 托管 |
| 持久层-搜索 | spring-data-elasticsearch | SB4 托管 |
| 缓存 | Spring Cache + Lettuce Redis | SB4 托管 |
| 消息队列 | rocketmq-spring-boot-starter | 2.3.6 |
| 服务间通信 | HTTP Service Clients（@HttpExchange + RestClient，替代 Dubbo） | — |
| 对象存储 | aliyun-sdk-oss | 3.18.5 |
| 即时通讯 | 环信 IM（REST + SDK） | — |
| 人脸/地图 | 百度 AI 开放平台 | — |
| 离线推荐 | Apache Spark MLlib + mongo-spark-connector | 4.2.0 / 11.1.0 |
| 工具 | Lombok / commons-io / commons-codec | 2.22.0 / 1.23.0 |

### 前端

| 端 | 技术 | 版本 |
|------|------|------|
| 管理端 | Vue + Element Plus + ECharts + Pinia + Vue Router + Axios + Vite + TypeScript | 3.5.44 / 2.14.7 / 6.1.0 / 4.1.0 / 4.6.4 / 1.20.0 / 8.3.5 / 6.0.3 |
| 用户端 | uni-app + Vue + uview-plus + Pinia + vue-i18n + dayjs + Sass | 3.0.0-alpha / 3.5.44 / 3.8.130 / 4.1.0 / 11.4.16 / 1.11.23 / 1.106.1 |
| 用户端构建 | Vite（受 DCloud 版本钳制）+ TypeScript + vue-tsc | 5.2.8 / 5.9 / 3.3.13 |

---

## 三、目录结构说明

```
xfjy/
├── pom.xml                     # 根 pom：SB 4.1.1 parent + 依赖版本集中管理
├── xfjy-server/                # ★ 主业务服务（18081）
│   └── 用户资料/推荐/圈子/视频/IM/设置 + 管理后台接口 + Sa-Token 认证
├── xfjy-dubbo/                 # ★ 后端数据服务
│   ├── xfjy-dubbo-interface/   #   内部 HTTP 接口定义（已无 Dubbo，纯 HTTP Service Clients）
│   └── xfjy-dubbo-service/     #   MongoDB 数据读写 + ID 生成（18083）
├── xfjy-elasticsearch/         # ★ 地理位置服务：ES 附近的人搜索
├── xfjy-recommend/             # ★ 推荐评分消费者：RocketMQ 消息 → MongoDB 评分存储
├── xfjy-spark/                 # ★ 离线推荐计算：MLlib 协同过滤（Spark 4.2，Java 编写）
├── xfjy-admin/                 # ★ 管理后台前端（Vue3 + Element Plus + Vite 8）
├── xfjy-app/                   # ★ 用户端前端（uni-app 多端：H5/小程序/App）
├── docs/                       # 文档：需求文档、重构规划、数据库脚本 mytanhua.sql、logo
└── .creative-design/           # Logo 生成的一次性脚本（check_alpha.py 等）
```

### 各目录职责与处置建议

| 目录 | 职责 | 状态/建议 |
|------|------|------|
| `xfjy-server` | 主业务 HTTP 服务，含管理后台 Admin 系列接口 | ✅ 保持 |
| `xfjy-dubbo` | interface（接口定义）+ service（MongoDB 数据服务） | 🔧 **需重构**：已无 Dubbo，建议重命名为 `xfjy-service` / `xfjy-api`，消除误导 |
| `xfjy-elasticsearch` | ES Geo 查询（UserLocation） | ✅ 保持 |
| `xfjy-recommend` | MQ 消费者，写推荐评分 | ✅ 保持 |
| `xfjy-spark` | 离线批量推荐计算 | ✅ 保持；运行时需 Spark 冒烟验证 |
| `xfjy-admin` | 管理后台前端 | ✅ 保持（技术栈已是最新） |
| `xfjy-app` | 用户端多端应用 | ⚠️ 受 DCloud alpha 版本钳制，正式构建前关注正式版 |
| `docs/` | 需求/规划文档、SQL | ✅ 保持 |
| `.creative-design/` | Logo 生成的临时脚本 | 🗑️ **可删除**（产物已入库 docs/logo，脚本无复用价值） |
| `.joycode/` | 旧编辑器的记忆/规则目录 | 🗑️ **可删除**（与本项目构建无关，或加入 .gitignore） |
| `docs/心飞为你飞.jpg` | 旧版推广二维码 | 🗑️ **可删除**（README 已不再引用） |

---

## 四、架构总览

| 服务 | 端口 | 职责 |
|------|------|------|
| xfjy-sso（外部） | 18080 | 单点登录（手机号+验证码，JWT 签发）——**尚未纳入本仓库** |
| xfjy-server | 18081 | 主业务 + 管理后台 |
| xfjy-dubbo-service | 18083 | MongoDB 数据服务 |
| xfjy-elasticsearch | — | 附近的人 |
| xfjy-recommend | — | MQ 评分消费者 |
| xfjy-spark | — | 离线推荐（定时任务） |

**数据链路**：用户行为 → xfjy-server → RocketMQ → xfjy-recommend → MongoDB 评分表 → Spark 定时 ALS 计算 → 推荐列表 → Redis 缓存 → 接口消费。

---

## 五、快速开始

```bash
# 后端（JDK 25 + Maven 3.9+）
mvn clean compile

# 管理后台前端
cd xfjy-admin && npm install && npm run dev

# 用户端（H5）
cd xfjy-app && npm install && npm run dev:h5
```

依赖的中间件：MySQL、Redis、MongoDB、Elasticsearch、RocketMQ；外部服务：环信 IM、阿里云 OSS、百度 AI、SSO（18080）。

---

## 六、文档

- 📄 [需求文档](docs/需求文档.md)
- 📄 [项目重构规划](docs/项目重构规划.md)
- 📄 [升级任务计划](docs/升级任务计划.md)
- 🗄️ [数据库脚本](docs/mytanhua.sql)
