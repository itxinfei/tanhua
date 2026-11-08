# 贡献指南

欢迎参与心飞交友（xfjy）的开发。本文件说明提交改动前需要知道的约定；更完整的架构、接口与代码约束见仓库根目录的 `AGENTS.md`（以该文件为准）。

## 环境准备

| 项 | 版本 |
|---|---|
| JDK | 25 |
| Node | 22.23.4 LTS（根目录 `.nvmrc`） |
| Maven | 3.9.x（CI 镜像固定 3.9.16） |
| 包管理器 | npm（仅使用 `package-lock.json`） |

中间件（MySQL / MongoDB / Redis / Elasticsearch / RocketMQ / MinIO / Nacos / Qdrant / Neo4j / WuKongIM / LiveKit）通过 `deploy/docker-compose.middleware.yml` 启动。**仓库中的部署配置带有一套本地开发环境的内网地址与默认口令，仅适用于本机或内网开发；换环境前需要逐项替换，不要把默认口令带到可公网访问的环境。**

## 提交流程

1. Fork 仓库，基于 `master` 创建特性分支，命名如 `feat/xxx`、`fix/xxx`。
2. 提交信息使用 Conventional Commits：`type(scope): 中文简述`，`type` 取 `feat` / `fix` / `refactor` / `docs` / `test` / `chore`。
3. 一个 PR 只做一件事，不混入全仓格式化、依赖升级或无关重命名。
4. 提交前必须通过本地编译门禁（见下），未通过不得提交。

## 本地编译门禁

```bash
# 后端
mvn -q -DskipTests compile

# xfjy-app（uni-app）
npm run build:h5 --prefix xfjy-app
npm run type-check --prefix xfjy-app

# xfjy-admin
npm run build --prefix xfjy-admin
npm run lint:style --prefix xfjy-admin

# 改动涉及规则文件时
pwsh scripts/sync-agent-rules.ps1 -Check
```

只跑与本次改动相关的部分即可，但结果必须全绿；验证失败时修复后新建一笔提交，不要改写上一笔提交。

## 仓库收录白名单

本仓库的 `.gitignore` 是「先忽略全部、再逐条放行」的白名单制，只收录必须的代码和必要的文件。以下内容不入库：

- `.env`、任何密钥、口令、证书、私钥；
- `node_modules`、`dist`、`target`、日志、临时脚本、截图产物、本地工具缓存；
- 各 AI / IDE 工具的专属规则目录与本地 board；
- 需求文档、内部审查与审计报告、AI 交接记录等内部过程资料。

新增文件类型若需要入库，先确认白名单已放行，再提交；不要用 `git add -f` 绕过。

## 代码约束要点

- 调用链固定：网关 `8090` → 聚合层 `18081` → 领域服务，仅网关对外；端口表不得自行调整。
- Controller 不写业务逻辑，Service 不拼 HTTP 响应；入参用 DTO、出参用 VO，不裸返回实体。
- 一张表 / 一个集合只有一个写入方，事务只加在写方 Service。
- 新增业务异常只用 `BusinessException(code, message)`，不新增全局异常处理器。
- 线程池必须显式 `new ThreadPoolExecutor(...)`，禁止 `Executors` 快捷方法。
- 权限、接口契约、前端视觉的完整规则见 `AGENTS.md` §2–§7。

## 行为与安全

参与讨论与评审请遵守 `CODE_OF_CONDUCT.md`。发现安全漏洞**不要公开提 Issue**，按 `SECURITY.md` 的渠道私下报告。
