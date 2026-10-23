---
name: 心飞交友全栈重构规划
description: 微服务化四阶段拆分进度（阶段四收尾完成556c063）+编排层决策+幂等方案
type: project
---

文档：`docs/项目重构规划.md`。SB4.1.1+JDK25，@HttpExchange/RestClient替代Dubbo。

**进度（2026-10-23）**：
- ✅ 阶段一：gateway(8080)+sso(18080)
- ✅ 阶段二(a4a48d9)：user-service(18090)
- ✅ 阶段三(ad0d51e)：circle(18084)/video(18085)/match(18082)/message(18086)+data-core退役
- ✅ 阶段四(556c063)：端口冲突修复+中间件地址统一+MQ消费幂等+gateway编排层注释
- ⬜ 远期：server拆空后删除(现仍是编排层+管理后台)；P1新建pay-service

**拓扑**：gateway(8080)/sso(18080)/server(18081编排)/match(18082)/circle(18084)/video(18085)/message(18086)/recommend(18087)/elasticsearch(18088)/user(18090)

**Why:** 编排层(BFF)决策——前端请求统一gateway→server，server契约调用各域服务，不直连；评分消息幂等用Redis SETNX(业务键+事件时间戳,TTL 24h)防重复累计。
**How to apply:** 后续新域服务沿用编排模式加契约代理；MQ消费者必须带幂等；端口分配参考拓扑勿冲突；HuanXinApiImpl环信REST仍是TODO桩需真实对接。