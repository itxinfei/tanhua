# xfjy-server — 主业务聚合服务

心飞交友的核心后端服务（端口 18081），承担三重职责：C 端业务聚合编排、管理后台 API、推荐评分引擎。作为边缘服务，经 `@HttpExchange` 调用各拆分域服务，前端流量由 `xfjy-gateway` 路由进入。

## 核心职责

| 域 | 说明 |
|----|------|
| 用户资料 | 资料读写、照片墙（photo_album JSON）、头像 OSS 上传（`POST /upload/image`） |
| 探花匹配 | 今日佳人（TodayBest）、卡片推荐、混合评分编排 |
| 推荐引擎 | 冷启动召回 + Qdrant 向量 + Neo4j 二度好友 + 距离衰减 + 画像相似度互惠精排 |
| 圈子/视频 | 经 `QuanZiApi` / `VideoApi` 聚合动态与视频流 |
| 消息/IM | WuKongIM 直连凭证签发 + LiveKit 1v1 音视频（`HuanXinController` 已删除，现走 IMProviderDelegate 双通道） |
| 通知设置 | 喜欢/评论/公告/匹配四开关持久化 |
| 管理后台 | `/admin/**` 内容审核、举报、公告、用户管理（Sa-Token 鉴权） |
| 运行监控 | Actuator：`/actuator/health|info|metrics` |

## 鉴权

| 拦截器 | 路径 | 说明 |
|--------|------|------|
| `TokenInterceptor` | `/**`（排除 `/admin/**`、`/actuator/**`、`/error`） | C 端用户 token 校验 |
| `AdminTokenInterceptor` | `/admin/**`（排除 `/admin/login`） | 管理员 Sa-Token 校验 |

## 推荐评分配置

```yaml
xfjy:
  recommend:
    scoring:
      w-vector: 0.4       # PyTorch 双塔 Qdrant Cosine 权重（enabled:false 时自动降级）
      w-neo4j: 0.2        # Neo4j 二度好友扩散权重（enabled:false 时自动降级）
      w-geo: 0.25          # Haversine 距离衰减权重
      w-profile: 0.15      # 画像相似度（tags Jaccard + 年龄差 + 同城）权重
      geo-decay-km: 50
    filter:
      opposite-sex: true
    cold-start:
      enabled: true
      fallback-size: 20
  qdrant:
    host: 127.0.0.1
    port: 6333
    collection: user_embedding
  neo4j:
    uri: bolt://127.0.0.1:7687
```

Qdrant / Neo4j 未部署时自动降级为空召回，不阻断主链路。当前端到端推荐链路默认走 Qdrant 向量召回 + Neo4j 二度好友 + 距离 + 画像加权混合评分。

## 第三方依赖

- MinIO：图片/视频上传（`minio.properties` 本地配置，缺失时可启动、上传快速失败）
- WuKongIM + LiveKit：IM 长连接 + 1v1 音视频（`application-local.yml` 保留 `huanxin:` 双通道回退配置）
- 百度 AI：人脸认证（可选）

## 启动

```bash
mvn -pl xfjy-server -am spring-boot:run
```

依赖 Nacos / MySQL / Redis 先就绪，MongoDB / ES / RocketMQ 按需启动。