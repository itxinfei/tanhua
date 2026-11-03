# AI Agent 规则入口

**唯一事实源：[`AGENTS.md`](../../AGENTS.md)。本文件不复制任何规则正文。**

## 1. 读取顺序
1. 读 `AGENTS.md` 全文。
2. 按 `AGENTS.md` §10 路由表，最多再读 3 份配套文档。
3. 工具专属配置与 `AGENTS.md` 冲突时，一律以 `AGENTS.md` 为准（§0）。

## 2. 各工具从哪里读
见 [`tool-matrix.md`](tool-matrix.md)。该表是团队唯一需要记住的清单：
工具 / 入口文件 / 类型（指针·生成·直读）/ 官方支持是否核实 / 读取时机 / 维护方式。

## 3. 变更流程（一个事实只写一处）
1. 规则变更只改 `AGENTS.md`（或它在 §10 指向的专业文档）。
2. 生成类入口不手改，改完真源后运行 `pwsh scripts/sync-agent-rules.ps1 -Write`。
3. PR 必须写「规则变更理由」；无规则变更写「无」（`AGENTS.md` §9 第 6 条）。

## 4. 历史迁移记录
- `.joycode/rules/security-no-secrets.md`：要求 `${ENV_VAR:default}` 占位符，与
  `docs/需求文档/02-技术约束.md` 的 `CONST-SEC-005`（开发/测试明文、禁止占位符）相反，
  已删除，按实测口径（`application*.yml` 中 `${}` 残留 0 处）取 SEC-005。
- `.joycode/rules/frontend-xfjy-app.md`：内容已并入 `AGENTS.md` §7 与
  `docs/需求文档/03-开发规范.md` §三；其中「触摸目标 ≥44rpx」与 `AGENTS.md` 的 88rpx 冲突，
  以 88rpx 为准。
- `.joycode/memory/*.md`：JoyCode 本机记忆，不属于团队规则，继续留在 `.joycode/`（不入库）。
  已核对：`feedback_frontend_quality.md` 与真源一致（tabBar 两处配置已在 §7 写实）；
  `feedback_no_secrets_in_repo.md` 的「内网开发凭证可入库」与 §6 口径有差异，见待决策项。
