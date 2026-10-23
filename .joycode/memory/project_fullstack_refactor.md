---
name: 心飞交友全栈重构规划
description: 微服务化四阶段拆分进度（阶段三完成ad0d51e）+data-core已退役+评分消息归属原则
type: project
---

文档：`docs/项目重构规划.md`。SB4.1.1+JDK25，@HttpExchange/RestClient替代Dubbo。

**进度（2026-10-23）**：
- ✅ 阶段一：gateway+sso(18080)
- ✅ 阶段二(a4a48d9)：user-service(18090)
- ✅ 阶段三(ad0d51e)：circle(18084)/video(18085)/match(18082)/message(18086)+data-core退役
- ⬜ 阶段四：收尾

**拓扑**：gateway(18080)/sso(18080)/server(18081纯编排)/match(18082)/circle(18084)/video(18085)/message(18086)/user(18090)

**Why:** 评分消息"谁的数据谁发送"，recommend以JSON String解析零改动兼容；环信集成属消息域，编排层仅契约调用。
**How to apply:** 阶段四注意：HuanXinApiImpl环信REST仍是TODO桩需真实对接；gateway路由/messages/**,/huanxin/**仍指向server（合理）；replace_file行范围替换易残留，优先精确内容匹配。