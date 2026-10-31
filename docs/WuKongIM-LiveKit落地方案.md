# WuKongIM + LiveKit 落地方案（心飞交友）

> 编写时间：2026-10-31。所有端口、协议、依赖均来自官方文档/仓库一手核实；未实测项显式标注。
> 目标：以全开源（Apache-2.0）、可私有化、资源可控的组合，替换环信 IM 与环信音视频。

## 一、架构与职责分工

```
uni-app(xfjy-app)                         Spring 微服务
  ├─ WuKongIM JS SDK  ──消息/信令──>          xfjy-message-service（业务、鉴权）
  ├─ 调 /api/token 拿 LiveKit JWT  <──签发──  livekit-server SDK（io.livekit:livekit-server）
  └─ livekit client  ──音视频媒体──> LiveKit SFU
WuKongIM：连接/消息/离线消息/群聊/在线状态（内置存储，无外部依赖）
LiveKit ：WebRTC SFU，媒体转发（单节点无需 Redis）
```

- **WuKongIM 只做消息与信令通道**，不内置媒体服务器；通话的呼叫/振铃/挂断等控制信令可作为自定义消息走 WuKongIM。
- **LiveKit 只做媒体**，客户端凭后端签发的短期 JWT 加入房间。
- 两者解耦，各自独立扩容；后端不暴露任何 secret 给客户端。

## 二、部署（已在本仓库落地）

已新增/修改：
- `deploy/docker-compose.middleware.yml`：新增 `wukongim`、`livekit`，卷 `wukongim-data`（现共 13 个服务）。
- `deploy/livekit/config.yaml`：LiveKit 生产配置。
- `deploy/.env.example`：新增 `WUKONGIM_EXTERNAL_IP`、`LIVEKIT_URL/API_KEY/API_SECRET`。

启动：

```bash
cd deploy
cp .env.example .env   # 填入并修改密钥
docker compose -f docker-compose.middleware.yml up -d wukongim livekit
```

端口与防火墙：

| 服务 | 端口 | 协议 | 用途 |
|---|---|---|---|
| wukongim | 5001 | HTTP | 管理/REST API |
| wukongim | 5100 | TCP | 客户端长连接 |
| wukongim | 5200 | WS | WebSocket 连接 |
| wukongim | 5300 | HTTP | 管理界面 |
| livekit | 7880 | TCP/WS | 信令/房间 API（建议配 TLS） |
| livekit | 7881 | TCP | WebRTC over TCP 回退 |
| livekit | 50000-60000 | **UDP** | 媒体（每参与者约 2 端口，必须放行） |

> 镜像使用了 daocloud 加速前缀；若拉取失败，改为官方 `wukongim/wukongim:latest`、`livekit/livekit-server:latest`。
> WuKongIM 数据目录 `/root/wukongim` 与健康端点 `/health` 来自官方 v2 文档；若用 v3 镜像，请按该版文档复核数据路径。

## 三、后端集成（Spring）

### 1. 依赖（Maven，坐标已核实）

```xml
<dependency>
  <groupId>io.livekit</groupId>
  <artifactId>livekit-server</artifactId>
  <!-- 版本以 Maven Central 最新为准（2026-07 已发布，0.13.x 在用） -->
  <version>0.13.0</version>
</dependency>
```

### 2. 配置（application-*.yml）

```yaml
livekit:
  url: ${LIVEKIT_URL:ws://192.168.20.133:7880}
  api:
    key: ${LIVEKIT_API_KEY:xfjykey}
    secret: ${LIVEKIT_API_SECRET}
```

### 3. 签发 token 的控制器（基于官方 Spring Boot 示例精简）

```java
@RestController
@RequestMapping("/api")
public class TokenController {
    @Value("${livekit.api.key}")    private String apiKey;
    @Value("${livekit.api.secret}") private String apiSecret;
    @Value("${livekit.url}")        private String serverUrl;

    @PostMapping("/token")
    public ResponseEntity<?> getToken(@RequestBody TokenRequest req) throws Exception {
        // TODO: 在此接入 Sa-Token 登录校验，确认当前用户身份与房间归属
        String room = req.getRoomName() != null ? req.getRoomName()
                : "room-" + Instant.now().getEpochSecond();
        AccessToken at = new AccessToken(apiKey, apiSecret);
        at.setIdentity(req.getParticipantIdentity());
        at.setName(req.getParticipantName());
        VideoGrant g = new VideoGrant();
        g.setRoomJoin(true);
        g.setRoom(room);
        g.setCanPublish(true);
        g.setCanSubscribe(true);
        at.addGrant(g);
        Map<String,String> resp = new HashMap<>();
        resp.put("server_url", serverUrl);
        resp.put("participant_token", at.toJwt());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }
}
```

### 4. 通话信令流程（呼叫/挂断走 WuKongIM）

1. 主叫通过 WuKongIM 向被叫发自定义消息（类型如 `call_invite`，含 room 名）。
2. 双方各自调 `POST /api/token` 拿到同一 room 的 JWT。
3. 客户端用 token 连 LiveKit 房间，发布/订阅音视频轨道。
4. 任一方挂断：发 `call_end` 信令并断开 LiveKit 连接；房间空闲后 LiveKit 自动回收。

## 四、uni-app 集成（含关键风险，务必先读）

> **风险（未实测，需 POC）**：`livekit-client` 与 WuKongIM JS SDK 依赖标准浏览器 WebRTC/WebSocket。
> 在 uni-app **原生 App（App-WebView/原生渲染）** 中 WebRTC 不能保证可用，纯 npm 安装不一定能直接跑通；H5 端则可直接使用。

可选两条落地路径：

- **路径 A（成本低，先验证）**：把音视频通话页用 **H5/webview 承载**（H5 工程内 `npm i livekit-client`），uni-app 通过 web-view 打开，URL 带 token。最快跑通，体验接近但非纯原生。
- **路径 B（体验好，成本高）**：基于 LiveKit 官方 **Android/iOS 原生 SDK**，用 **UTS/原生插件**封装为 uni-app 模块（类似野火的 uni-chat-uts 做法）。WuKongIM 同理用其 JS SDK 或原生封装。

H5 端参考代码：

```ts
// npm i livekit-client
import { Room, Track } from 'livekit-client';
const r = await fetch('/api/token', { method:'POST',
  body: JSON.stringify({ roomName:'room_1', participantIdentity:'u_1001', participantName:'心飞' })
}).then(x=>x.json());
const room = new Room();
await room.connect(r.server_url, r.participant_token);
room.localParticipant.enableCameraAndMic();
```

## 五、分阶段实施

1. **P1 基础设施**：启动 wukongim/livekit，H5 端跑通消息 + 1v1 通话（路径 A）。
2. **P2 后端对接**：xfjy-message-service 接入 token 签发与 Sa-Token 鉴权，下线环信强集成与 `HANXIN_*`。
3. **P3 原生 App**：按 POC 结果决定 UTS 原生封装（路径 B），离线推送配合 uni-push 2.0。
4. **P4 扩容**：WuKongIM 多节点、LiveKit 加 Redis 与多节点；配置 webhook 与录制（egress）。

## 六、验证清单（实测后勾选）

- [ ] `curl http://<ip>:5001/health` 返回正常；管理台 5300 可登录。
- [ ] 防火墙 UDP 50000-60000 放行，弱网下 TCP 7881 回退生效。
- [ ] H5 两用户同房间可双向音视频；挂断后房间自动释放。
- [ ] uni-app 原生端按选定路径完成 POC，确认 WebRTC 可用性。
- [ ] 密钥已替换默认值，secret 不出现在客户端包内。

## 七、已核实 / 待核实边界

- 已核实：WuKongIM Apache-2.0、端口、数据目录/健康端点；LiveKit Apache-2.0、端口范围、config 结构；Java 坐标 `io.livekit:livekit-server`。
- 待核实（环境所限未实测）：本机 docker 不可用，未做容器端到端；v3 镜像数据路径；uni-app 原生 App 内 WebRTC 兼容性——这两项必须在目标机/真机 POC 后再下结论。

## 八、参考来源

- WuKongIM 单节点 Docker：https://mint.githubim.com/en/installation/docker/single-node
- WuKongIM 配置/端口：https://mint.githubim.com/en/server/configuration ；https://github.com/WuKongIM/WuKongIM
- LiveKit 部署与端口：https://docs.livekit.io/oss/deployment/ ；config 样例：https://github.com/livekit/livekit/blob/master/config-sample.yaml
- LiveKit Spring Boot 示例：https://docs.livekit.io/frontends/build/authentication/endpoint/
- Java SDK 坐标：https://mvnrepository.com/artifact/io.livekit
