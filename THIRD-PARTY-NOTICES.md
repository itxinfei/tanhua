# 第三方组件清单与许可证核对

本文件是**索引**，用于说明本项目用了哪些第三方组件、以及怎样核对它们各自的许可证。它不代为声明任何第三方组件的许可条款，也不构成法律意见。分发、打包镜像或商用前，请以各组件随分发提供的 `LICENSE` / `NOTICE` 文件为准逐项复核。

## 后端

| 组件 | 用途 | 许可证线索 |
|---|---|---|
| Spring Boot / Spring Framework / Spring Cloud / Spring Cloud Alibaba | 服务框架、依赖注入、服务发现 | Apache-2.0 |
| MyBatis / MyBatis-Plus | 持久层 | Apache-2.0 |
| Sa-Token | 登录鉴权 | Apache-2.0 |
| Apache RocketMQ、Nacos | 消息与服务注册 | Apache-2.0 |
| spring-security-crypto（BCrypt） | 后台口令哈希 | Apache-2.0 |

## 前端

| 组件 | 用途 | 许可证线索 |
|---|---|---|
| Vue 3、Pinia、Vite | 前端框架、状态、构建 | MIT |
| Element Plus | 后台组件库 | MIT |
| TypeScript | 类型系统 | Apache-2.0 |
| Apache ECharts | 后台图表 | Apache-2.0 |
| uni-app / uni-ui（DCloud） | 多端运行时与组件 | **需核对**：以其官方仓库当前 `LICENSE` 为准 |
| UnoCSS、Phosphor Icons | 原子样式与图标 | 需核对各自仓库 `LICENSE` |

## 中间件与基础设施（作为独立服务经网络调用）

| 组件 | 用途 | 许可证线索 |
|---|---|---|
| Qdrant、LiveKit、WuKongIM | 向量检索、音视频、即时通讯 | Apache-2.0 |
| Redis 7.x | 缓存与限流 | BSD-3-Clause（7.x） |
| Elasticsearch、MongoDB、MinIO、Neo4j、MySQL | 检索、文档库、对象存储、图库、关系库 | **重点核对**：这些项目采用 copyleft 或 source-available 类许可，不同版本条款不同 |

> **核对提示**：以上中间件以独立进程 / 容器方式经网络调用，通常不会把许可条款传染到本仓库源码；但一旦把它们打进交付镜像、二次分发或对外提供托管服务，必须逐项核对对应版本的许可与商标条款。`deploy/` 下的编排文件只描述连接方式，不授予任何第三方组件的使用权。

## 算法与工具链

| 组件 | 用途 | 许可证线索 |
|---|---|---|
| PyTorch | 双塔模型训练（`xfjy-recsys`） | BSD-3-Clause |
| JDK 25、Maven、Node.js、npm | 构建工具链 | 各自官方许可 |

## 复核命令

```bash
# 后端：列出实际解析到的依赖树
mvn dependency:tree

# 前端：列出实际安装的包
npm ls --all --prefix xfjy-app
npm ls --all --prefix xfjy-admin

# Python：列出训练环境依赖
pip freeze
```

将输出中的groupId / 包名与对应官方仓库的 `LICENSE` 文件比对后，再更新本文件。若发现缺失或过期条目，请提交 PR 补充证据（官方许可证文件链接或随包 `LICENSE` 内容）。

本项目自身的许可证见根目录 `LICENSE`（Apache License 2.0），归属声明见 `NOTICE`。
