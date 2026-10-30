# Gitee（主仓）→ Gitea（CI）触发配置

目标：开发照常 `git push` 到 **Gitee**，由 Gitea 拉取镜像后自动跑 CI/CD。
Gitee 只作为受控主仓，**CI 不会向 Gitee 写回任何东西**，也不随意 push。

---

## 1. 准备 Gitea 仓库

1. 打开 `http://192.168.20.133:3000`，用管理员登录（首次启动按引导初始化）。
2. 新建仓库 `xfjy`（仓库名已由 mytanhua 改名而来），**或** 直接把它设为 Gitee 的镜像仓：
   - 新建仓库时勾选「镜像仓库」，源地址填 `https://gitee.com/itxinfei/mytanhua.git`（需 Gitee 只读账号/令牌）。
   - 这样 Gitee 有更新后，Gitea 可在「镜像同步」时拉取最新代码（含 `.gitea/workflows/deploy.yml`）。
3. 仓库 → **Settings → Actions → Secrets**，新增：
   - `SSH_DEPLOY_KEY`：内容为原 Jenkins 用的部署私钥（与宿主机 `~/.ssh/authorized_keys` 中公钥配对的那一份）。

> 若不想用镜像仓，也可在 Gitea 建普通仓，手动 `git push gitea master` 一次把代码推过去；之后每次改完再推。

---

## 2. 注册 act_runner

1. Gitea 后台 → **管理 → Runner → 创建 Runner**，复制生成的注册令牌。
2. 写入 `deploy/docker-compose.gitea.yml` 的 `GITEA_RUNNER_TOKEN` 环境变量（建议用 `.env` 或 shell export，勿入库明文）。
3. 构建 CI 镜像（必须先在宿主机跑，镜像才进宿主 docker，act_runner 才能拉起）：
   ```bash
   docker build -f deploy/gitea/Dockerfile.ci -t xfjy/ci:latest deploy/gitea
   ```
4. 启动：
   ```bash
   docker compose -f deploy/docker-compose.gitea.yml up -d
   ```
5. 回到 Gitea Runner 页面，应看到 `xfjy-runner` 状态为 Active。

---

## 3. 让 Gitee 推送触发 Gitea CI

Gitea Actions 在 Gitea 仓库收到「push」时触发。Gitee 是主仓，所以需要把 Gitee 的推送事件转达给 Gitea。两种做法：

### 方案 A（推荐）：Gitee webhook 触发 Gitea 镜像同步
在 **Gitee 仓库 → 管理 → WebHooks → 添加**：
- URL：`https://<GITEE_TOKEN>@192.168.20.133:3000/api/v1/repos/admin/xfjy/mirror-sync`
- 方式：`POST`
- 触发事件：Push
- 说明：Gitee 推送后调用该接口让 Gitea 立即拉取镜像；同步产生 push 事件即触发 Actions。

> 若发现镜像同步未触发 Actions（个别版本行为差异），用方案 B。

### 方案 B（更稳）：Gitee webhook 直接触发 workflow_dispatch
在自己的服务器放一个小脚本（或用 gitea 的 `/repos/{owner}/{repo}/actions/workflows/deploy.yml/dispatches` API），由 Gitee webhook 调用它，携带 Gitea 个人令牌触发 `workflow_dispatch`。该接口等价于在 Gitea Actions 页面手动「Run workflow」。

---

## 4. 验证

1. 本地 push 到 Gitee master。
2. 等几秒，Gitea 仓库 → **Actions** 应出现新 run；观察各阶段日志。
3. 成功后登录服务器 `192.168.20.133`：
   - `docker ps --filter name=xfjy-` 应看到 13 个容器（11 后端 + admin + h5）处于 Up 状态。
   - `/home/itxinfei/logs/` 下日志（仅历史 host 进程用）不再产生；容器日志用 `docker logs xfjy-server` 查看，应出现 `Started ... Application`。
4. 接口验证：`curl http://192.168.20.133:8090/actuator/health`；浏览器访问 `http://192.168.20.133:5174`（admin）、`http://192.168.20.133:5173`（h5）。

---

## 与原 Jenkins / host 进程部署的差异

| 项 | Jenkins(host 进程) | Gitea Actions(Docker) |
|----|---------|---------------|
| 服务 | xfjy-jenkins 容器 | xfjy-gitea + xfjy-act-runner 容器 |
| 流水线定义 | `Jenkinsfile` | `.gitea/workflows/deploy.yml` |
| 触发 | Gitee webhook（原未配） | Gitee webhook → Gitea 镜像同步/dispatch |
| 工具 | 全局工具 jdk25/maven39/node22 | CI 镜像 `xfjy/ci:latest` 内置（含 docker-cli） |
| 部署产物 | jar 经 SSH 投递 + `nohup java -jar` | 构建 Docker 镜像并 `docker compose up -d` 运行容器 |
| 前端 | 不部署（本地跑） | admin/h5 打成 nginx 容器（:5174/:5173） |
| 资源占用 | 重（JVM + 插件） | 轻（Go 二进制 + 按需拉起 job 容器） |

> 注意：CI 镜像 `xfjy/ci:latest` 现在额外包含 docker CLI + compose 插件，因为 job 容器需通过挂载的 `/var/run/docker.sock` 在宿主机构建镜像、运行应用容器（单主机，免镜像仓库）。务必先在宿主机执行 `docker build -f deploy/gitea/Dockerfile.ci -t xfjy/ci:latest deploy/gitea`。
