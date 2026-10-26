# xfjy-recommend — 推荐评分消费者

订阅用户行为消息、计算评分并落库的异步服务。消费圈子与视频两大行为流，产出推荐引擎所需的评分数据。

## 消费拓扑

| Topic | 消费组 | 生产方 | 触发行为 |
|-------|--------|--------|----------|
| `xfjy-quanzi` | `xfjy-quanzi-consumer` | xfjy-circle-service | 动态发布/点赞/评论 |
| `xfjy-video` | `xfjy-video-consumer` | xfjy-video-service | 视频发布/点赞/关注 |

## 数据链路

```
用户行为（圈子/视频）
  → RocketMQ topic
  → QuanZiMsgConsumer / VideoMsgConsumer
  → 评分计算（互动权重）
  → MongoDB 评分集合
  → xfjy-server 推荐接口混合评分消费
```

## 启动

```bash
# 需先启动 RocketMQ NameServer + Broker + MongoDB
mvn -pl xfjy-recommend -am spring-boot:run
```

## 配置

```yaml
rocketmq:
  name-server: ${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}
  producer:
    group: xfjy-recommend
```

RocketMQ 不可用时消费端无法启动，生产端（circle/video）发送失败仅记日志不阻断主流程。