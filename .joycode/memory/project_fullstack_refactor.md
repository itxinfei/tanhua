---
name: 心飞交友全栈重构规划
description: 前端uni-app选型+后端升级成果+微服务化拆分进度（阶段一/二已完成）及阶段二遗留偏差决策
type: project
---

心飞交友（xfjy）重构规划文档位于 `docs/项目重构规划.md`。

**前端**：uni-app Vue3 + uview-plus + Pinia + TS，多端一套代码，IM用环信SDK，地图用高德插件。
**后端**：Spring Boot 4.1.1 + JDK 25 升级完成，@HttpExchange/RestClient 替代 Dubbo。

**微服务化进度（2026-10-22）**：
- ✅ 阶段一：gateway(8080)+sso(18080)
- ✅ 阶段二(a4a48d9)：user-service(18090)关系域，契约零改动，server代理拆两工厂@Qualifier区分
- ⬜ 阶段三：circle/video/match/message 内容域
- ⬜ 阶段四：删 data-core 与 server 残壳、补幂等

**阶段二偏差（重要）**：user-service 只迁了 Mongo 关系域，MySQL 的 tb_user_info/tb_settings 未迁。原因：server 内 8 个服务直接依赖 UserInfoService 构造 QueryWrapper，无法直接过 HTTP。UserInfo 查询契约化（userIds/keyword/gender API）将在阶段三拆内容域时顺带完成。

**Why:** 用户以资深全栈架构师角色推进四阶段微服务化，阶段二已确认完成。
**How to apply:** 后端接口对齐规划文档P0/P1/P2；做阶段三时必须记得顺带把 UserInfo 查询契约化迁走，这是阶段二遗留的隐含任务。