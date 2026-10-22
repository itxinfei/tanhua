---
name: 心飞交友全栈重构规划
description: 前端uni-app选型决策+后端升级成果+重构规划文档位置，全栈项目当前阶段
type: project
---

心飞交友（xfjy）项目已完成全栈重构规划，文档位于 `docs/项目重构规划.md`。

**前端选型（用户确认）**：uni-app Vue3 v5.03 + HBuilderX 5.15 + uview-plus 3.4.17 + Pinia + Vite + TypeScript 5.6，一套代码多端（App+小程序+H5）。IM沿用环信SDK，地图用高德uni-app插件。

**后端**：Spring Boot 4.1.1 + JDK 25 升级已完成并验证（8/8模块BUILD SUCCESS），@HttpExchange/RestClient替代Dubbo。

**产品参考**：探探（tantanapp.com），核心玩法=卡片滑动匹配+IM+LBS附近的人+圈子动态+小视频。

**Why:** 用户以资深全栈架构师角色要求输出参考探探的完整重构规划，前端选型由用户在提问后选择uni-app。
**How to apply:** 后续前端开发任务基于uni-app技术栈；后端接口设计需对齐重构规划文档中的功能需求（P0/P1/P2优先级）；新增功能先查规划文档避免偏离基线。