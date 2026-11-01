# 事故复盘：LiveKit 万级 UDP 端口映射导致 dockerd 卡死

**日期**：2026-11-01
**影响**：VM (192.168.20.133) SSH 完全不可达，所有 Docker 容器操作（start/stop/rm/inspect）挂死
**恢复**：强制重启 VM，LiveKit 改用 `--network host` 模式

---

## 一、根因分析

### 1.1 触发操作

在 `docker-compose.middleware.yml` 中，LiveKit 服务配置了：
```yaml
ports:
  - "50000-60000:50000-60000/udp"   # 10000 个 UDP 端口
```

执行 `docker compose -d livekit` → `docker start xfjy-livekit` → `docker rm -f xfjy-livekit` 时，dockerd 在处理 10000 条 iptables NAT 规则时卡死。

### 1.2 技术链路

```
docker compose up -d livekit
  └─> dockerd 分配 10000 个 iptables DNAT 规则
       ├─> 写入 /proc/net/ip_tables_names
       ├─> 占用大量内核内存（conntrack 表扩展）
       └─> dockerd main loop 阻塞
            ├─> 后续所有容器 API 调用挂起
            ├─> containerd → dockerd gRPC 超时
            └─> SSH 会话因系统负载过重无法 fork 新 bash 进程（banner 超时）
```

### 1.3 为什么 1000 端口也不够

后期改到 1000 端口后重启仍然卡死——原因：
- 旧容器（10000 端口版）Exited 状态占用大量 conntrack 槽位
- `docker rm -f` 需要遍历清理这些残留条目
- dockerd 单线程锁导致清理期间所有新请求排队

### 1.4 为什么不提前发现

| 设计缺陷 | 后果 |
|----------|------|
| 没有 `deploy/ci/` 脚本对端口数量做上限校验 | 配置端可自由写 50000-60000 这样的范围 |
| CI 编译 job 没有 `validate-compose.sh` 静态检查 | 高成本配置直到部署时才暴露 |
| `ps aux | grep docker stats` 多次后台运行 | 系统资源被元操作消耗 |

---

## 二、修复方案对比

| 方案 | 优点 | 缺点 | 最终选择 |
|------|------|------|----------|
| bridge + 3000 端口 | 与传统部署一致 | dockerd 仍然较卡 | ✗ |
| bridge + 1000 端口 | iptables 较小时可工作 | 旧容器残留导致 rm 仍卡 | ✗ |
| **host network** | **零 iptables，秒级启动，零内存额外开销** | 端口直露宿主机（内网可接受） | **✓** |
| docker socket bind | 无 | 安全问题和兼容性 | ✗ |

**host network** 的核心优势：
- 容器直接用宿主机的网络命名空间
- 无 iptables NAT、无 conntrack、无端口映射
- LiveKit 进程直接 bind 到 7880/7881/50000-51000 on the host

---

## 三、预防措施

### 3.1 静态校验脚本

CI 增加 `deploy/ci/validate-compose.sh`，校验规则：
- 单服务 ports 总数 ≤ 50（UDP 端口范围 ≤ 20）
- 禁止出现 "5xxxx-6xxxx" 这样的万级端口范围
- network_mode: host 的服务不得再声明 ports

### 3.2 部署时熔断

`manage.sh` 增加 port-count 预检：
```bash
MAX_PORTS=50
ACTUAL=$(grep -oE '[0-9]+-[0-9]+' docker-compose.*.yml | awk -F- '{print $2-$1}' | sort -rn | head -1)
if [ "$ACTUAL" -gt "$MAX_PORTS" ]; then
  echo "[FATAL] 端口范围 $ACTUAL 超过上限 $MAX_PORTS"
  exit 2
fi
```

### 3.3 健康检查降级感知

LiveKit healthcheck 应区分"端口耗尽"和"进程存活"：
```yaml
healthcheck:
  test: ["CMD-SHELL", "ss -uln | grep -q '7880' || exit 1"]  # 直接检测 socket
```

### 3.4 会话级清理规则

> **铁律**：所有 `*.sh` 脚本如果是构建 / 部署辅助工具，必须在对应的 `stage-artifacts.sh` 末尾清理。CatPaw 后台任务的输出文件（临时的 `/tmp/*.sh`）也须在 up -d 成功后立即删除。

---

## 四、清理清单（2026-11-01 执行）

| 位置 | 文件 | 动作 |
|------|------|------|
| workspace | `deploy/ci/start-livekit.sh` | ✅ 已删除 |
| workspace | `deploy/ci/check-vm.sh` | ✅ 已删除 |
| workspace | `deploy/manage.sh.bak` | ✅ 已删除 |
| workspace | `deploy/manage.sh.bak2` | ✅ 已删除 |
| workspace | `deploy/tmp_sync/` | ✅ 已删除 |
| VM (133) | /tmp/start-lk.sh, /tmp/start-lk.log | ✅ 已删除 |
| VM (133) | /tmp/fix-all.sh, /tmp/fix-result.log | ✅ 已删除 |
| VM (133) | 已停止的容器 | ✅ docker container prune -f |
| VM (133) | 旧 exited LiveKit (10000 端口版) | ✅ 随容器清理 |

---

*本报告由 CatPaw 高级开发工程师编写 — 2026-11-01*
