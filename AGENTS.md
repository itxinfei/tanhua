# AGENTS.md — mytanhua（心飞交友 xfjy）开发约束

本文件是本仓库唯一的规则真源。所有 AI 工具（Claude Code / Codex / JoyCode / Qoder /
Copilot / Serena / Trae）开工前必须读完本文件。
`docs/需求文档/02-技术约束.md`、`03-开发规范.md` 只作解释与示例，不得与本文件冲突；
冲突时以本文件为准。

## 0. 优先级与冲突处理
1. 安全与数据一致性（§3、§6）> 接口契约（§5）> 代码风格（§2、§7）> 工具本地规则。
2. 工具本地规则（`.joycode/rules`、`.claude/board`、`.workbuddy/memory`）只能补充，不得覆盖本文件。
3. 规范与代码不一致时：禁止自行二选一。在 `docs/需求文档/01-需求文档.md` §11.7
   追加一行（证据:文件:行 / 两种说法 / 影响），停工等人裁决；裁决后只改本文件。
4. 不确定时先读 §10 路由表；仍不确定则按第 3 条登记，禁止凭常识补全。
5. 本文件由人维护；AI 不得直接改，需在 PR 描述中给出「规则变更理由」。

## 1. 技术栈锁定（禁止擅自升级或替换）
| 项 | 版本/选型 | 禁止 |
|---|---|---|
| JDK | 25（`<java.version>25</java.version>`；CI 镜像 `eclipse-temurin:25-jdk-jammy`、运行 `25-jre-jammy`，本地 JDK 25） | 降级到 17/21 |
| Node | **22.23.4 LTS**。唯一真源仅两处：`deploy/gitea/Dockerfile.ci` 的 ARG `NODE_VERSION` 与根 `.nvmrc`（值必须一致）；本地用 nvm/fnm 按 `.nvmrc` 切换；前端 `engines.node>=22.12.0` 仅表能力下限 | 升到 23/24 及以上或降级到 22.12 以下；在 workflow/job 内用 `n` 临时切版本（版本必须烤进 CI 镜像）；改版本不同步这两处 |
| Maven | **3.9.16**（`deploy/gitea/Dockerfile.ci` ARG `MAVEN_VERSION`；本地 3.9.x） | 另引 mvnw 或其它版本造成分叉 |
| Spring Boot | 4.1.1（父 POM 统一管理） | 3.x；引入 `spring-boot-starter-security` |
| Spring Cloud / Alibaba | 2025.1.3 / 2025.1.0.0 | 混用其他 BOM |
| 持久层 | MyBatis-Plus 3.5.17（`mybatis-plus-spring-boot4-starter` + `jsqlparser`） | JPA/Hibernate |
| 鉴权 | Sa-Token 1.46.0（仅 `xfjy-server`、`xfjy-sso` 使用） | JWT、Spring Security |
| 消息 | RocketMQ 2.3.6：topic `xfjy-quanzi`（circle 生产）/ `xfjy-video`（video 生产），均由 recommend 消费 | 新增 Kafka/RabbitMQ |
| 主题命名 | 只用 `xfjy-quanzi` / `xfjy-video`；禁止新建未登记 topic | 改 topic 名（需双端同改 + 上线顺序） |
| 文档库 | MongoDB，配置键 `spring.mongodb.uri` | `spring.data.mongodb.*` |
| 缓存 | Redis 7.x | 用本地缓存替代跨服务状态 |
| 对象存储 | MinIO（仅 `xfjy-server`） | 直传本地磁盘 |
| 图库 | Neo4j（仅 `xfjy-server`，须可降级） | 强依赖 |
| 注册中心 | Nacos（仅 discovery） | 启用 Nacos Config、`spring.config.import` |
| 前端（uni-app） | `@dcloudio/*` 全量 `3.0.0-5030420261029002`（详表见 §7.3） | 降回 5020 系列、单独改某包 patch、新增非 npm 锁文件 |
| 禁止引入 | H2、Dubbo、Feign、Sentinel、Knife4j | — |

校验：`rg -n "sentinel|dubbo|feign|knife4j|spring-boot-starter-security" --glob "pom.xml"` → 0
校验：`rg -n "N_NODE_MIRROR|n 2[0-9]+\.|N_PREFIX" .gitea/workflows` → 0（workflow 内禁止临时切 Node）；
`rg -n "NODE_VERSION=" deploy/gitea/Dockerfile.ci` 的值必须等于 `cat .nvmrc`（22.23.4）

## 2. 模块、端口与分层
- Maven 聚合 12 个模块（根 `pom.xml`）：`xfjy-gateway`、`xfjy-sso`、`xfjy-user-service`、
  `xfjy-circle-service`、`xfjy-video-service`、`xfjy-match-service`、`xfjy-message-service`、
  `xfjy-api`、`xfjy-server`、`xfjy-recommend`、`xfjy-elasticsearch`、`xfjy-pay-service`。
  `xfjy-admin`、`xfjy-app`、`xfjy-recsys` 不在聚合模块内，禁止当作 Maven 模块引用。
- 调用链只允许：网关 `xfjy-gateway:8090` → BFF `xfjy-server:18081` → 领域服务。
- 端口固定（`CONST-DEPLOY-001`）：8090 网关 / 18080 sso / 18081 server / 18082 match /
  18084 circle / 18085 video / 18086 message / 18087 recommend / 18088 elasticsearch /
  18089 pay / 18090 user-service；前端 5173（app H5）、5174（admin）。只允许网关对外。
- 包结构：`controller` / `service` / `mapper`（或 `repository`）/ `entity` / `dto` / `vo` / `config`。
  Controller 不写业务逻辑，Service 不拼 HTTP 响应。
- DTO 只用于入参，VO 只用于出参，实体不得直接作为接口出参。

校验：`rg -c "\s*<module>" pom.xml` → 12（新增模块必须同步本表与端口表）

## 3. 数据归属（一张表/一个集合只能有一个写入方）
- MySQL：写入方以 `02` CONST-ARCH-004 表为准；BFF 禁止新增持久化表。
- MongoDB：集合写入方只允许一个服务；跨服务读取走 API，禁止两个服务写同一集合。
- 禁止在 BFF 为领域服务补写业务表；禁止双写（已修复项不得回退）。
- 事务只加在写方 Service；跨服务一致性用「本地事务 + 消息/补偿」，禁止分布式事务框架。

校验：`rg -n "tb_pay_order|tb_vip_package" xfjy-server/src/main/java` → 只允许读

## 4. 鉴权链（顺序不可调换）
1. 网关 `RateLimitGlobalFilter`：仅 POST 的 `/movements`、`/comments`、`/small-videos`、
   `/users/report`，5 次/60s/IP，超限 429；Redis 故障降级放行。
2. BFF `TokenInterceptor`：回源 `xfjy-sso` 校验 token。
3. 后台 `AdminTokenInterceptor` + `@RequireRole`（`/admin/**` 业务 Controller 类上必须有；
   `AdminController` 为登录/自身账户维护入口，按 §11 豁免）。
4. 领域服务 `XInternalTokenInterceptor`：`X-Internal-Token` 不匹配返回 403。
- 领域服务只信任内部 token，禁止信任外部传入的用户身份头。

校验：`rg -L "@RequireRole" --glob "Admin*Controller.java" xfjy-server/src/main/java`（存量 1 处豁免）

## 5. 接口契约
- 响应形态（2026-11-03 裁决，与现状一致）：Controller 返回 `ResponseEntity<VO>`（既有形态，
  存量 55 处，见 §11）；错误/异常响应由 `GlobalExceptionHandler` 统一写
  `com.xfjy.server.response.Result<T>{code,message,data}`；下载/SSE/Actuator 除外。
  禁止裸返回实体；**禁止新增** `ResponseEntity<Map>` / `ResponseEntity<List>`（存量 43 处，见 §11）。
  前端 `utils/request.ts` 对两种形态均兼容（`body.code !== undefined` 宽松判断），新增接口不得依赖该兼容层做双形态。
- 错误码：成功 `200`；业务错误段 `1000 用户 / 2000 内容 / 3000 支付 / 4000 后台`。
  HTTP 状态码与 `code` 分离：`401` 未登录、`402` 需付费、`403` 无权限、`404` 不存在、`409` 冲突。
- `GlobalExceptionHandler` 已实现且唯一：
  `BusinessException`→400、参数校验→400、`SwipeLimitException`/`BenefitLimitException`→402、
  `NoHandlerFoundException`→404、`NotLoginException`→401、`NotRoleException`/`NotPermissionException`→403、
  `DuplicateKeyException`→409、`ResourceAccessException`→503、其他→500。
  拦截器仍直接 `setStatus(401/403)` 且不写响应体，属既有形态，不在本次整改范围。
- 业务异常只用 `BusinessException(code,message)`；禁止新增第二个 `@RestControllerAdvice`；
  禁止 Controller 内 `catch(Exception)` 后手写状态码。
- 分页入参 `page` / `pageSize`（默认 1 / 10，上限 100）；禁止 `pagesize` / `page_size` / `size`
  （存量 `pagesize` 33 处见 §11，随重构迁移）。
- 分页出参 `com.xfjy.service.vo.PageInfo{total,pageNum,pageSize,records}`。
- 时间 `yyyy-MM-dd HH:mm:ss`（`Asia/Shanghai`）；例外：列表出参中需前端二次格式化的时间字段
  允许毫秒时间戳 `Long`，字段名以 `Time` 结尾并在 VO 注释中说明（如 `visitTime`）。
  金额用 `BigDecimal`，禁止 `double`。

校验：`git diff -- '*/controller/*.java' | rg "^\+.*(ResponseEntity<(Map|List)|catch\s*\(Exception)"` → 0

## 6. 配置与密钥
- 开发/测试：`application*.yml` 允许明文，**禁止** `${VAR:default}` 占位符（与 `CONST-SEC-005` 一致）。
- 敏感信息分级（2026-11-03 裁定）：**内网地址与内网凭据不作为风险处理**——`192.168.x`、`10.x`、`172.16-31.x` 等私网 IP、本机虚拟机账号密码属本地开发环境，允许出现在配置与文档中，**不报警、不改写**。
- **公网 IP / 云服务器地址 / 公网主机名 + 任何凭据一律禁止进入仓库**（含代码、配置、README、示例、compose、CI 变量说明、文档截图）；发现即删除并提示轮换对应凭据（公网主机可达，凭据泄露即可被直接利用）。
- 边界：`CONST-SEC-005` 约束**写法**（内网环境用明文、禁 `${VAR:default}`）；本条约束**范围**（公网地址与凭据不得入库），二者不冲突。
- 生产：数据库/Redis 密码、AK/SK、第三方密钥只允许部署注入；密钥不得写入代码、注释、测试、README，模板文件只放空值键名。
- MongoDB 必须写 `spring.mongodb.uri`；Redis/DataSource 前缀不变。
- 新增环境变量必须同步部署变量表与 `deploy/.env.example`（若不存在则先建）。
- Nacos 只用于发现；禁止引入配置中心。

校验：`rg -n "\$\{[A-Z_]+:" --glob "xfjy-*/src/main/resources/*.yml"` → 0；
`rg -n "spring.data.mongodb" --glob "*.yml"` → 0

## 7. 前端（xfjy-app + xfjy-admin）

### 7.1 xfjy-app（uni-app + rpx）
- `uview-plus` **已移除且禁止加回**：全仓无 `<u-*>`/`$u-` 使用；`uni-icons.vue` 本身不含该 `@use`，报错来自 `uni.scss` 被 uni-app 注入每个组件后在 `node_modules` 下无法解析包路径。
- 不引入其它第三方 UI 组件库（含 `uview-plus`：已移除，勿再加回；其 `@use` 若写进 `uni.scss` 会被注入每个组件并在 `node_modules` 下解析失败）；组件只用 `uni-ui`（easycom `^uni-(.*)`）与 `src/components/*`。
- 颜色只允许 `var(--color-*)`（定义在 `xfjy-app/src/App.vue`）；禁止在页面写主色字面量。
- 主色 `--color-primary`=`#fe3c72`；浅底小字用 `--color-primary-text`=`#c2185b`（对比度 ≥4.5）。
  `--color-primary-deep` 当前值为 `#b13bff`（历史紫，与主色不同源），禁止新代码使用。
  旧冷粉 `#ff5e9c` 已废弃，禁止出现。
- 图标只用 UnoCSS Icons + Phosphor：`i-ph-*`；禁止新增 `uni-icons`、禁止回退字体图标。
- 按钮必须使用 `a11y-button` 组件（`src/components/a11y-button.vue`，全站按钮单一真源）；禁止新增原生 `<button>` 或给普通 `<view>` 绑 `@tap/@click` 模拟按钮（返回键等也须用它，最小热区 88rpx 见上条），存量原生按钮随重构迁移。
- 可点击元素最小热区 **88rpx**；触摸目标另需满足无障碍基线（对比度 ≥4.5:1）。
  例外：`a11y-button size="sm"`（56rpx）仅限列表行内紧凑操作（取消/喜欢/移除等），
  且该行主体必须可点击进详情作为主热区；独立功能按钮（提交、确认、退出等）禁止用 `sm`。
- 移动端单手好用为第一前提：页面/卡片左右边距必须**对称**，禁止单侧贴边；任何元素不得
  溢出视口，页面禁止出现横向滚动（滚动容器 `overflow-x: hidden`），内容超高时只允许纵向滚动。
- 块级/卡片元素统一 `box-sizing: border-box`，`width:100%` 时 padding 必须计入宽度；
  flex/grid 子项显式写 `min-width:0`（纵向布局用 `min-height:0`），防止被内部宽内容撑破容器、
  吃掉对侧留白（user-list-item 右侧留白丢失即此问题，已修复项不得回退）。
- 同页列表/表单行的左右内边距、行高、热区保持一致；右侧固定操作区（按钮/开关/箭头）
  与左侧内容对称留白，禁止贴边。
- 尺寸单位用 `rpx`；1px 边框、tabbar 对齐等物理像素例外并注释原因。
- 禁止写死白底/黑字，必须走 CSS 变量以自动适配暗色。
- tabBar 配置只有两处：`pages.json` 的 `tabBar.list`（编译期，MP 端）与
  `xfjy-app/src/config/tab-config.ts`（H5 自绘用）；两者无法自动联动，改 tab 项必须同步，
  禁止在任何页面内再起第三套 tabbar。
  tabbar 定制图形为 `xfjy-app/scripts/gen-icons.mjs` 生成的 PNG（原 `gen-tabbar-icons.py` 已删除废弃），
  禁止手工改图；改图标必须重跑脚本。
- 新增通用样式进 `uni.scss` 或全局 class，禁止复制粘贴。

### 7.2 xfjy-admin（Vue 3 + Element Plus + SCSS）视觉约束
- 颜色/间距/字号/圆角/阴影只允许 `var(--xfjy-*)`，真源 `src/style.css`；
  `.vue`/`.scss` 禁止出现十六进制、`rgb()`、`hsl()` 字面量（`src/style.css` 除外）。
- 间距只允许 `--xfjy-space-1..8`（4/8/12/16/20/24/32px）；禁止 13/18/22px 等非栅格值。
- 字号单页 ≤4 档，只从 `--xfjy-font-size-{xs,sm,base,md,lg,xl}` 选；
  正文不得小于 `--xfjy-font-size-sm`（13px）。
- 主色（`--xfjy-primary` 及 `--xfjy-gradient-*`）同屏 ≤3 处；状态色只作 Tag/状态点/图标，
  禁止作主按钮底色（功能色已由 `--el-color-*` 映射，禁止再定义第二套）。
- 圆角只用 `--xfjy-radius-{sm,md,lg,xl}`；卡片固定 `--xfjy-radius-lg`（12px），
  同页禁止混用 4/6/8/12；阴影只用 `--xfjy-shadow-{card,hover,dropdown}`，列表卡片禁止 hover 抬升。
- 骨架组件强制：列表页 `PageHeader + SearchBar + TableCard`；数值概览 `StatCard`；状态 `StatusTag`。
  禁止手写 div 卡片/搜索栏/状态点；确认框用 `ElMessageBox.confirm`，空态 `el-empty`，
  加载 `v-loading`/`el-skeleton`，一手日期区间用单个 `el-date-picker type="daterange"`。
- 表格：操作列固定右侧，列宽 ≥80px，行高 44–48px（`--xfjy-table-row-height`），
  禁止横向溢出页面容器；数字列 `font-variant-numeric: tabular-nums`。
- 表单：`label-width` 统一 96px，一行 ≤3 字段；详情用 `el-descriptions`（2 列 border），
  禁止用 `el-table` 冒充键值对。
- 禁止 `!important` 覆盖 EP 内部样式；确需穿透必须 `:deep()` 且同行注释原因。
- 视觉验收：每页 1280/1440 各截图一次，与 `src/views/user/index.vue` 并排比对
  标题字号/卡片圆角/按钮高度；`npm run lint:style` 新增代码零 warning。

校验（`git diff HEAD` 覆盖已暂存改动；排除 token 定义文件；CSS 变量定义行与注释行不计命中，
命令内已直接过滤）：
`git diff HEAD -- 'xfjy-app/src/**' ':(exclude)xfjy-app/src/App.vue' ':(exclude)xfjy-app/src/uni.scss' | rg "^\+.*(uni-icons|#fe3c72|#ff5e9c)" | rg -v -- "--color-|^\+\s*(//|/\*|\*)"` → 0；
`npm run lint:style --prefix xfjy-admin 2>&1 | rg "✖"` → 0（新增代码）

### 7.3 前端版本锁定（uni-app）

版本表（真源：`xfjy-app/package.json` + `xfjy-app/package-lock.json`）：

| 包名 | 锁定精确版本 | 锁定理由 |
|---|---|---|
| `@dcloudio/uni-app`、`uni-app-harmony`、`uni-app-plus`、`uni-components`、`uni-h5`、`uni-mp-alipay`、`uni-mp-baidu`、`uni-mp-harmony`、`uni-mp-jd`、`uni-mp-kuaishou`、`uni-mp-lark`、`uni-mp-qq`、`uni-mp-toutiao`、`uni-mp-weixin`、`uni-mp-xhs`、`uni-quickapp-webview`（16 个运行时包） | `3.0.0-5030420261029002` | 运行时与构建链必须同 patch，否则编译器按解析路径选版本 |
| `@dcloudio/vite-plugin-uni`、`uni-automator`（构建链基准） | `3.0.0-5030420261029002` | 基准版本，运行时向它对齐，不得反向自选"最新 patch" |
| `@dcloudio/uni-cli-shared` | `3.0.0-5030420261029002`（全仓仅 1 份） | 编译器核心；两份并存会让构建结果不可复现，只能由 `overrides` 钉死 |
| `@vue/*`（`compiler-core`/`compiler-dom`/`compiler-sfc`/`compiler-ssr`/`shared`/`server-renderer`） | `3.5.44`（与顶层 `vue` 同版本，全仓仅 1 份） | 由 `overrides` 收敛，禁止再引入第二版本（`@dcloudio/*` 默认会拉 3.4.21） |
| `@intlify/*`（`core-base`/`message-compiler`/`shared`） | `11.4.16`（全仓仅 1 份） | 与 `vue-i18n` 精确版配套；由 `overrides` 收敛，禁止再引入第二版本 |
| 包管理器 / 锁文件 | 仅 npm + `package-lock.json` | CI 三条流水线均为 `npm ci` |

硬规则（可 grep）：
- 禁止出现 `3.0.0-5020620260917001` 及任何 `-5020…` 旧系列：`rg -n "3\.0\.0-5020" xfjy-app --glob "!node_modules"` → 0。
- 禁止单独升级/降级某个 `@dcloudio/*` 到不同 patch：`dependencies` 与 `devDependencies` 中 `@dcloudio/*` 版本值必须全为 `3.0.0-5030420261029002`。
- `uni-cli-shared` 只允许一份，且必须来自 `overrides`；禁止删除 `overrides` 段。
- `vue-i18n` 必须钉精确版本 `11.4.16`（禁止写 `^`）：`^` 会解析到 11.4.18，与 `@intlify/*` 的 11.4.16 override 冲突并让 `npm ls` 报 invalid。
- 禁止新增 `pnpm-lock.yaml`、`yarn.lock`、`bun.lockb`；包管理器只允许 npm。
- 升级必须整批同版本：先改 `package.json`（含 `overrides`），删 `package-lock.json` 后 `npm install` 重生成，禁止只改 lock。
- 升级或改版本后必须重跑：`npm run type-check`、`npm run build:h5`、`npm run build:mp-weixin`，三条全绿才可提交。

校验命令（在 `xfjy-app` 下执行，可复制）：
```
npm ls @dcloudio/uni-cli-shared   # 期望全部 deduped，仅 1 个非 deduped 节点
npm ls @dcloudio/uni-h5           # 期望只有一个 3.0.0-5030420261029002
rg -o '"@dcloudio/[^"]+": "[^"]+"' package.json | rg -o '3\.0\.0-[0-9]+' | sort -u   # 期望只 1 行
Test-Path pnpm-lock.yaml          # 期望 False
```
注意 `npm ls uni-cli-shared`（不带 scope）会输出 `(empty)`，是假通过，必须用带 scope 的写法。

本节与代码/其它文档冲突时**以本节为准**，agent 不得自行变更版本或包管理器。

## 8. 提交与文档
- **本地编译门禁**：提交到 Gitea 前必须本地编译通过，未通过不得 commit、不得 push；跳过验证的提交视为违规。
- 最小验证集：后端 `mvn -o -q -DskipTests compile`（改了测试或核心逻辑改为 `mvn -o -pl <模块> test`）；前端 `xfjy-admin` 跑 `npm run build` 与 `npm run lint:style`（含 `stylelint-gate`）；规则文件改动跑 `pwsh scripts/sync-agent-rules.ps1 -Check`。
- 验证失败：修问题后**新建一笔提交**，不得 `--amend`、不得 `--no-verify`。
- 提交信息用 Conventional Commits：`feat|fix|refactor|docs|test|chore(scope): 描述`。
- 一个 PR 只做一件事；禁止混入全仓格式化、依赖升级、无关重命名。
- **仓库收录白名单**：Gitee 只收录「必须的代码和必要的文件」，`.gitignore` 为唯一准绳（说明见 `docs/仓库收录白名单.md`）；CLI 工具规则入口仅本地生效，**禁止入库**——`.trae`、`.cursor`、`.windsurf`、`.clinerules`、`.roo`、`.continue`、`.junie`、`.qoder`、`.serena`、`.joycode`、`.github/copilot-instructions.md`、`.aider.conf.yml`、`GEMINI.md`、`CONVENTIONS.md`；新增目录/文件类型须先在白名单登记再提交；禁止提交 `_` 前缀临时脚本、截图产物、`node_modules`、`dist`、`target`、日志、`.env`、本地 board。
- 白名单抽查：`git ls-files | rg '^\.(trae|cursor|windsurf|clinerules|roo|continue|junie|qoder|serena|joycode)/'`、`git ls-files | rg '^(GEMINI\.md|CONVENTIONS\.md|\.aider\.conf\.yml|\.github/copilot-instructions\.md)$'` → 均须 0 命中；`git ls-files | git check-ignore --no-index --stdin` → 无输出。
- 技术栈/中间件/CI 变更必须同步本文件 §1 与 `CHANGELOG.md` 顶部。
- 规则只改本文件；不得直接改各工具副本。

校验：`git log -1 --pretty=%s | rg "^(feat|fix|refactor|docs|test|chore)"`

## 9. 完成定义（唯一验收清单）
1. `mvn -q -DskipTests compile` 通过（改动模块及其下游）。
2. 前端改动：`xfjy-app` 跑 `npm run build:h5` + `npm run type-check`；
   `xfjy-admin` 跑 `npm run build` + `npm run lint:style`。脚本名以各 `package.json` 为准。
3. 本文件 §1–§8 的「校验」命令无**新增**命中；存量走下方豁免清单。
4. 接口改动附 curl/示例响应，字段与 §5 一致。
5. 数据改动说明写入方与回滚方式；跨服务改动附调用链。
6. PR 描述含「改了哪些规则/为什么」，无规则变更写「无」。

## 10. 任务路由（最小必读集，≤3 份）
| 任务 | 必读 |
|---|---|
| 后端接口/业务 | 本文件 §2/§3/§5 + `02-技术约束.md` 对应 CONST 节 |
| 前端页面/样式 | 本文件 §7 + `docs/icon-icons.md`（仅图标） |
| 部署/CI | 本文件 §1/§6 + `.gitea/workflows/ci.yml` |
| 数据/表结构 | 本文件 §3 + `02-技术约束.md` CONST-ARCH-004 |
| 需求实现 | `01-需求文档.md` 对应 FR + `04-用户限制矩阵.md` |
| 文档导航 | `00-需求文档导航总纲.md` |
| 规则入口（工具适配） | `docs/agent-rules/00-index.md` → `docs/agent-rules/tool-matrix.md` → 本文件 |

## 11. 存量违规豁免（2026-11-03 二次实测，新增行仍受约束）
| 项 | 存量 | 处理 |
|---|---|---|
| `ResponseEntity<Map|List>`（xfjy-server） | 43 处 | 新增禁止，存量随重构迁移 |
| Controller 内 `catch(Exception)` | 146 处 | 新增禁止 |
| `uni-icons`（xfjy-app/src） | 33 处 / 11 文件（按 `<uni-icons` 组件实例化计数） | 冻结，禁止新增 |
| 页面硬编码 `#fe3c72` | 代码级 0 处（2026-11-03 已收口为 `var(--color-primary)`）；残留 11 处均为注释说明 + `AppTabbar.vue` 局部 token 定义（tabbar 挂 body 无法继承 page 级变量） | 新增禁止 |
| 原生 `<button>`（xfjy-app/src） | 6 处（`components/error-boundary.vue` 2、`components/match-popup.vue` 2、`pages/chat/notifications.vue`、`pages/match/guide.vue` 各 1；`mine/change-phone`、`mine/verify`、`mine/settings` 已迁移 `a11y-button`） | 冻结，禁止新增，随重构迁移 `a11y-button`（独立功能按钮不得用 `sm`，见 §7.1） |
| `ResponseEntity<VO>` 裸 VO 返回（xfjy-server controller） | 55 处 | **裁决为既有形态而非违规**（2026-11-03）：§5 已按现状改写；约束仅为「禁止裸返回实体、禁止新增 `ResponseEntity<Map\|List>`」 |
| 分页入参 `pagesize`（xfjy-server 存量接口） | 33 处 | 新增必须 `pageSize`（§5），存量随重构迁移，迁移时前后端需同改 |
| 非主色硬编码 hex（xfjy-app/src） | 主体已收口（2026-11-03，~200 处 → `var()`：`--text-inverse`/`--bg-card`/`--success`/`--gold*`/`--info-blue*`/`--bg-deep*` 等，token 见 `App.vue`）；残留 43 行代码均为结构性约束——21 处 `uni-icons color="…"` 原生属性、5 处 `AppTabbar.vue` 局部 token 定义（body 挂载）、3 处 `linear-gradient` 多停点、2 处 `confirmColor` 原生 API、12 处状态色对/第三方品牌色（network-status-bar、客服状态徽章、微信/朋友圈图标色） | 新增禁止；残留冻结（各类均有不可 token 化原因），收口随组件级重构 |
| `/admin/**` 缺 `@RequireRole` | 1 处（`AdminController`） | **免登录入口 + 自身账户维护**，不补注解。依据：`AdminController` 仅 `POST admin/login`、`GET admin/info`、`PUT admin/password`、`POST admin/logout`、`PUT admin/profile`；`WebConfig` L28-30 已将 `/admin/login` 从 `AdminTokenInterceptor` 排除，其余路由依赖拦截器填充的 `AdminThreadLocal`，非业务控制器。 |
| `xfjy-admin` 硬编码颜色（除 404 插画 4 处外） / 魔法间距 | 0 / 14 条（13 条 6px 间距 + 1 条侧栏折叠态内距，2026-11-03 收口后实测，原 26 / 38） | 新增禁止；颜色统一走 `--xfjy-*` token（本轮补充 `--xfjy-success-tint` / `--xfjy-danger-tint` / `--xfjy-primary-tint`，取值与替换前 hex 完全相同）。`npm run lint:style` 目标 **0 warning**，`npm run lint:style:gate` 不得超过 `xfjy-admin/stylelint-baseline.json` 基线（当前 total=0）；历史：65 → 43 → 19 → 0 |
| `xfjy-admin` 6px 间距豁免（13 条，另有侧栏折叠态 1 条，合计 14 条 `declaration-property-value-disallowed-list` 豁免） | `movement.vue` L386、`video.vue` L344（内联统计项分隔）；`kefu/workbench.vue` L422 聊天气泡内距、L448 语音图标对齐；`login/index.vue` L469 标题副文案；`review/index.vue` L543 键帽、L675 结论标签组、L754 横向滚动条留白；`report/index.vue` L472-473 头像与文字间距、L477-478 标签与昵称分隔（`.target-name`）、L569-570 横向滚动条留白；`user/detail.vue` L850 头像文字；`sensitive/index.vue` L59 敏感词标签间距（`.hit-word`） | 均为图标/文字对齐或滚动条留白类视觉微调，改 4px 或 8px 会破坏对齐；已用行级 `declaration-property-value-disallowed-list` disable 并注明理由，**禁止新增** |
| `xfjy-admin` 404 页插画配色 | 4 处 hex（`views/error/404.vue` L102-103/L118-119/L184-185/L191-192，均为 `color-no-hex` disable） | 配色随插画整体重做才有意义，暂不 token 化；已用行级 `color-no-hex` disable 并注明理由，**禁止新增** |
| `xfjy-admin` 侧栏折叠态内距 | 1 条（14 条间距豁免之一，`layout/components/Sidebar.vue` L211-212 `padding: 0 18px`） | 与 220px 栏宽的视觉微调，改 16px 会破坏图标对齐；已用行级 `declaration-property-value-disallowed-list` disable，**禁止新增** |
| `new SimpleDateFormat`（§13.4） | 6 处（`xfjy-server`：`AdminDashboardService` L119/L402、`AdminDashboardStatsService` L136/L224、`AdminOperationLogService` L48/L101，均为方法内局部变量，非 static 共享） | 新增禁止；存量随重构迁移 `DateTimeFormatter`，迁移前不得提升为字段/static |
| 日志 `+` 拼接（§13.5） | 5 处（`xfjy-server/IMController` L77、`xfjy-circle-service/QuanziApiImpl` L86、`xfjy-video-service/VideoApiImpl` L60、`xfjy-recommend/VideoMsgConsumer` L100、`QuanZiMsgConsumer` L146） | 新增禁止；存量改为 `{}` 占位符，随重构迁移 |

## 12. 未确认与变更
- 未确认统一登记：`docs/需求文档/01-需求文档.md` §11.7；格式
  `> 未确认：问题｜证据：文件:行｜选项 A/B｜建议`。
- 规则变更：改本文件 → 同步工具适配文件 → PR 中说明理由 → 合并。

## 13. 阿里巴巴开发规范强制项
后端 Java 代码遵循《阿里巴巴 Java 开发手册》强制项（P0 级），以下为结合本项目技术栈的落地约束。
与 §2/§5 重叠处以更严者为准；存量违规按 §11 豁免机制登记，新增禁止。

### 13.1 依赖管理
- 版本统一由根 pom `dependencyManagement` 管理；子模块声明依赖**禁止写 `<version>`**。
  根 pom 未管理的新依赖，先在根 pom 补管理条目，再在子模块无版本引入。
- `xfjy-api` 的 `optional` 依赖（spring-data-mongodb/spring-webmvc/jakarta.servlet-api 等）
  不向下游传染；使用 `@Document`/`ObjectId` 的宿主服务必须显式声明 `spring-data-mongodb`。
- 禁止引入未使用依赖；新增依赖前 grep 源码确认实际 import 需求。

校验：`rg -n "<version>" --glob "xfjy-*/pom.xml" | rg -v "project.version"` → 0

### 13.2 命名规约
- 类名 `UpperCamelCase`；数据传输对象加 `DTO` 后缀、视图对象加 `VO` 后缀、数据库实体不加后缀（存量形态）。
- 方法名/变量名 `lowerCamelCase`；常量全大写下划线分隔；包名全小写。
- 抽象类 `Abstract`/`Base` 开头；异常类 `Exception` 结尾；测试类 `被测类名+Test`。
- Service 方法命名：单个查询 `get`/`find`/`query`，列表 `list`/`queryXxxList`，分页 `queryPage`，
  计数 `count`，插入 `save`/`insert`，更新 `update`，删除 `delete`/`remove`。

### 13.3 OOP 与集合
- POJO 类属性用包装类型（`Long`/`Integer`/`Boolean`），禁止基本类型（数据库映射可能为 null，拆箱 NPE）。
- 所有 POJO（entity/dto/vo）必须有 `toString`（Lombok `@Data` 已含，禁止重复手写）。
- `equals` 用常量或确定非 null 对象调用：`"literal".equals(var)`，禁止 `var.equals("literal")`。
- 重写 `equals` 必须重写 `hashCode`。
- `foreach` 内禁止增删集合元素，用 `Iterator.remove()` 或 `removeIf`。
- 禁止调用 `@Deprecated` 方法（框架内部除外）。

### 13.4 并发与时间
- 线程池禁止 `Executors` 快捷方法创建，必须 `new ThreadPoolExecutor(...)` 显式指定核心参数。
- 禁止 `new SimpleDateFormat` 共享或 static 持有；日期格式化用 `java.time.format.DateTimeFormatter`（线程安全）。
- 禁止在循环体内创建线程/线程池。

### 13.5 异常与日志
- `catch` 后禁止空块吞异常；至少 `LOGGER.warn` 记录。
- 禁止丢失原始异常链：`catch (Exception e)` 后再抛必须 `new BusinessException(msg, e)` 传入 cause。
- 日志用 SLF4J 占位符 `{}`，禁止 `+` 拼接：`LOGGER.info("userId={}", id)`。
- 异常日志必须带业务上下文（userId/订单号等），禁止只打 `e.getMessage()`。

### 13.6 MySQL
- 自定义 SQL 禁止 `SELECT *`，必须写明列名（MyBatis-Plus 框架生成的 selectById/selectList 不受此限）。
- 新表必备 `id`/`created_at`/`updated_at`（字段命名 snake_case，与存量一致）。
- 禁止数据库外键，关联关系在应用层维护。
- 大字段（图片/文件）只存 MinIO URL，禁止入库。
- 分页必须走 `PaginationInnerInterceptor`，禁止手拼 `LIMIT`。

校验：
`rg -n "SELECT \*" --glob "*.xml" --glob "*.java" xfjy-*/src` → 0（自定义 SQL）
`rg -n "Executors\.new" --glob "*.java" xfjy-*/src` → 0
`rg -n "new SimpleDateFormat" --glob "*.java" xfjy-*/src` → 0
`rg -n "LOGGER\.(info|warn|error|debug)\(.*\+" --glob "*.java" xfjy-*/src` → 0（日志禁止拼接）
