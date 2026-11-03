# AI 工具规则入口矩阵

**唯一真源为仓库根 `AGENTS.md`。** 本表只记录「哪个工具从哪里读、何时读、谁维护」，不记录规则内容。
新增/调整任何入口前，先在本表登记，再改脚本清单（`scripts/sync-agent-rules.ps1`）。

## 1. 总表

| 工具 | 入口文件 | 类型 | 官方支持核实 | 读取时机 | 维护方式 |
|---|---|---|---|---|---|
| Codex / Amp / Jules / Zed / Trae(根) | `AGENTS.md` | 直读 | 已核实（AGENTS.md 跨工具事实标准） | 会话开始 | 人维护（唯一真源） |
| Claude Code | `CLAUDE.md` | 指针（`@AGENTS.md`） | 已核实（CLAUDE.md 支持 `@` 导入） | 会话启动 | 手写一行，不改正文 |
| Gemini CLI | `GEMINI.md` | 生成 | 已核实（GEMINI.md 为项目上下文文件） | 会话启动 | `sync` 脚本 |
| GitHub Copilot | `.github/copilot-instructions.md` | 生成 | 已核实（仓库自定义指令文件；Copilot 亦原生读 `AGENTS.md`） | 每次请求 | `sync` 脚本 |
| Cursor | `.cursor/rules/project-rules.mdc` | 生成（`alwaysApply: true`） | 已核实（`.cursor/rules/*.mdc`） | 每次请求 | `sync` 脚本 |
| Windsurf | `.windsurf/rules/project-rules.md` | 生成 | 已核实（`.windsurf/rules/` 新版） | 会话/请求 | `sync` 脚本 |
| Cline | `.clinerules/00-project-rules.md` | 生成 | 已核实（`.clinerules/` 目录） | 会话启动 | `sync` 脚本 |
| Roo Code | `.roo/rules/00-project-rules.md` | 生成 | 已核实（`.roo/rules/`） | 会话启动 | `sync` 脚本 |
| Continue | `.continue/rules/00-project-rules.md` | 生成 | 已核实（`.continue/rules/`，旧版走 config `rules`） | 会话启动 | `sync` 脚本 |
| JetBrains Junie | `.junie/guidelines.md` | 生成 | 已核实（Junie 项目指南文件） | 会话启动 | `sync` 脚本 |
| Aider | `CONVENTIONS.md` + `.aider.conf.yml`（`read:`） | 生成 + 配置指针 | 已核实（`read` 配置项） | 启动时读入上下文 | `sync` + 手改配置 |
| Trae | `.trae/rules/project_rules.md` | 生成 | **未核实**（按客户端项目规则约定） | 会话启动 | `sync` 脚本 |
| Qoder | `.qoder/rules/00-project-rules.md` | 生成 | **未核实**（本机 `.qoder/` 仅有 `settings.local.json`） | 会话启动 | `sync` 脚本 |
| AtomCode | 未建 | — | **未核实**（本机 `.atomcode/` 仅有 `artifacts/`、`local/`，无规则约定迹象） | — | 待核实后再建 |
| Serena | `.serena/project.yml` 的 `initial_prompt` | 指针（文本提示，无法 import） | 已核实（配置项存在） | 激活项目时自动注入 | 手写一行，不改正文 |
| WorkBuddy | `.workbuddy/memory/*.md` | 不适用（记忆，非规则） | **未核实** | 会话 | 不改，记忆归记忆 |

### 入库可见性（决定入口是否随仓库共享）

| 目录/文件 | git 可见 | 结论 |
|---|---|---|
| `GEMINI.md`、`CONVENTIONS.md`、`.aider.conf.yml`、`.github/`、`.cursor/`、`.windsurf/`、`.clinerules/`、`.roo/`、`.continue/`、`.junie/`、`.trae/` | 是 | 团队可见，clone 即生效 |
| `.qoder/` | **否**（`.gitignore:128`） | 仅本机生效；要共享需调整忽略规则（见待决策） |
| `.serena/` | **否**（`.gitignore:107`） | 仅本机生效；同上 |
| `.joycode/`、`.workbuddy/`、`.atomcode/`、`.claude/board/` | **否** | 本机工具/记忆目录，按设计不入库 |

`scripts/sync-agent-rules.ps1` 通过 `.gitignore` 白名单例外入库（`!/scripts/sync-agent-rules.ps1`），以便 CI 与全员复用。

### 未建旧格式的说明

- `.cursorrules`（Cursor 旧版单文件）：Cursor 当前版本以 `.cursor/rules/*.mdc` 为准；保留旧文件会让两套规则并存，故**不建**。
- `.windsurfrules`（Windsurf 旧版单文件）：同上，只建新目录格式。
- `AGENTS.md` 之外的 `.trae/rules/*`：Trae 同时读取根 `AGENTS.md`，此处仅为显式项目规则位，未核实优先级。

## 2. 同步脚本

脚本：[`scripts/sync-agent-rules.ps1`](../../scripts/sync-agent-rules.ps1)

```powershell
pwsh scripts/sync-agent-rules.ps1 -Check   # 只校验（默认），不同步则 exit 1
pwsh scripts/sync-agent-rules.ps1 -Write   # 以 AGENTS.md 为准重新生成全部生成类入口
```

脚本做三件事：
1. 逐个比对生成类文件与 `AGENTS.md` 的正文是否一致（忽略换行符差异），不一致则输出文件路径、行数与首个差异行；
2. 检查指针类文件（`CLAUDE.md`、`.aider.conf.yml`、`docs/agent-rules/00-index.md`、`.joycode/rules/00-index.md`）确实包含指向 `AGENTS.md` 的引用；
3. 任何一项失败即以非零退出码结束。

### 接入 CI

在 `.gitea/workflows/ci.yml` 的静态检查 job 末尾加一步（与现有 `mvn` 步骤并列，不改变触发条件）：

```yaml
      - name: Agent rules sync check
        shell: pwsh
        run: ./scripts/sync-agent-rules.ps1 -Check
```

也可本地挂 pre-commit：`pwsh scripts/sync-agent-rules.ps1 -Check || exit 1`。
未接入前，PR 描述必须贴出 `-Check` 的输出。

## 3. 维护约定

1. 规则只改 `AGENTS.md`；生成类文件出现任何手改都会在下次 `-Check` 中被判为不同步。
2. 新增工具：先核实官方路径 → 在 §1 登记 → 在脚本 `$generated` / `$pointers` 清单追加 → 跑 `-Write`。
3. 删除工具：从脚本清单移除 → 删除对应生成文件 → 在 §1 标注「已移除 + 原因」。
