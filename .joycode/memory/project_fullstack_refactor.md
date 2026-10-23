---
name: 心飞交友全栈重构规划
description: 微服务化四阶段完成+两轮体检通过+编排层决策+幂等方案
type: project
---

文档：`docs/项目重构规划.md`。SB4.1.1+JDK25，@HttpExchange/RestClient替代Dubbo。

**进度（2026-10-23）**：
- ✅ 阶段一~四完成：gateway(8080)/sso(18080)/user(18090)/match(18082)/circle(18084)/video(18085)/message(18086)/recommend(18087)/elasticsearch(18088)/server(18081编排)
- ✅ 健康体检(ec3b9dc)：sso库名xfjy统一+profile旧IP修复；MySQL/Mongo全xfjy、RocketMQ统一127.0.0.1:9876
- ✅ 深度体检(680d0a5+301cbaa)：TokenInterceptor ThreadLocal泄漏修复+75处printStackTrace清零(SLF4J)+域服务包名重构(dubbo.server→service)
- ⬜ 远期：server拆空后删除(编排层+管理后台)；P1 pay-service；环信REST真实对接；Nacos接入

**Why:** 编排层(BFF)——前端统一gateway→server，server契约调用各域服务不直连；MQ消费幂等Redis SETNX(业务键+时间戳,TTL 24h)。
**How to apply:** 新域服务沿用编排模式；MQ消费者必须幂等；端口参考拓扑勿冲突；契约接口双注解(@GetExchange+@GetMapping)。