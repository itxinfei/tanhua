---
name: 心飞交友全栈重构规划
description: 微服务化四阶段拆分进度（阶段三进行中）+data-core退役决策+评分消息归属原则
type: project
---

规划文档：`docs/项目重构规划.md`。前端uni-app Vue3+uview-plus+Pinia+TS；后端SB4.1.1+JDK25，@HttpExchange/RestClient替代Dubbo。

**进度（2026-10-23）**：
- ✅ 阶段一：gateway(8080)+sso(18080)
- ✅ 阶段二(a4a48d9)：user-service(18090)关系域
- 🔄 阶段三：circle(18084)/video(18085)/match(18082)/message(18086)
  - ✅ UserInfo契约化（4方法API，server旧实现全删）
  - ✅ circle/video评分消息迁入微服务内部（QuanziMQService/VideoMQService已删，server去rocketmq依赖+配置，纯编排层）
  - ⬜ match-service拆分 + data-core退役
  - ⬜ message-service拆分
- ⬜ 阶段四：收尾

**data-core退役决策**：data-core只剩RecommendUserApiImpl/UserLocationApiImpl/ESUserLocationApiImpl三实现，拆match-service时全部吸收进去即可退役。

**Why:** 评分消息归属原则"谁的数据谁发送"，消费端xfjy-recommend以JSON String解析保持零改动兼容。
**How to apply:** 拆match-service时必须吸收data-core三实现并退役该模块；视频互动双消息行为已保持（quanzi从circle发、video从video发）。