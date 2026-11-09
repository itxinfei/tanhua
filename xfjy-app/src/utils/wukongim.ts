/**
 * WuKongIM SDK 封装（wukongimjssdk）
 *
 * 背景：后端 IM 通道已切换为自建 WuKongIM（tb_sys_config.im.provider=wukongim），
 * 原 /huanxin/user 端点（HuanXinController 已删除）下发的凭证 provider=wukongim，原环信 SDK 无法用该凭证登录，
 * 导致消息链路整体不通。本模块按原 easemob.ts 的对外契约用 WuKongIM 实现，
 * 消息体契约保持不变：单聊文本消息；结构化消息以 JSON 文本承载
 * （{"type":"image|voice|location|card|match",...}，媒体本体存 MinIO，IM 只传 URL）。
 *
 * WS 地址：优先使用 GET /messages/im/credential 下发的 wsUrl，未下发时从网关基地址推导。
 */
import {
  WKSDK,
  ChannelTypePerson,
  Message,
  MessageText,
  Channel,
  SyncOptions,
  PullMode,
  ConnectStatus,
  type Conversation,
  type SendackPacket,
} from "wukongimjssdk";
import { BASE_URL, get, getToken } from "./request";
import { warn, warnAlways } from "@/utils/logger";
import {
  emitMatch,
  emitMessage,
  emitSystemNotice,
  type MatchEvent,
  type ReceivedMessage,
  type SystemNotice,
} from "./im-events";

/** 事件类型统一由零 SDK 依赖的 im-events 总线定义，此处 re-export 保持既有类型导入路径兼容；
 *  type-only 导入在编译期擦除，不会让类型消费方产生对 wukongimjssdk 的运行时依赖 */
export type { MatchEvent, ReceivedMessage, SystemNotice } from "./im-events";

/** 会话摘要（聊天列表展示用） */
export interface ChatSession {
  /** 会话对方用户 ID */
  conversationId: string;
  /** 最后一条消息文本 */
  lastMessage: string;
  /** 最后消息时间戳（毫秒） */
  lastTime: number;
  /** 未读消息数 */
  unreadCount: number;
}

/** 服务端下发的 IM 接入凭证（GET /messages/im/credential） */
export interface ImCredential {
  /** 业务用户ID（即 IM uid） */
  username: string;
  /** 服务端派生的 IM token（盐值不出服务端） */
  token: string;
  /** IM 通道标识：wukongim / huanxin */
  provider: string;
  /** IM 长连接地址（ws://host:5200） */
  wsUrl: string;
}

/**
 * 获取当前登录用户的 IM 接入凭证（uid + token + wsUrl + provider）
 * 凭证由服务端派生，前端不感知 token 生成规则，避免本地派生错误导致登录失败。
 *
 * @returns 凭证对象；网络/鉴权失败时返回 null（调用方静默降级）
 */
export async function getImCredential(): Promise<ImCredential | null> {
  try {
    const data = await get<ImCredential>("/messages/im/credential");
    return data || null;
  } catch {
    return null;
  }
}

/** 登录状态 */
let isLoggedIn = false;

/** ensureSelfIM 在途任务：并发调用合并为一次连接，避免重复 connect 顶号 */
let selfImTask: Promise<boolean> | null = null;

/** IM WS 端口（WuKongIM 默认 WebSocket 端口） */
const IM_WS_PORT = 5200;

/**
 * IM WS 兜底地址：仅当 BASE_URL 与当前页面主机都无法推导时使用。
 * 内网开发部署默认值，与后端 im.ws-url 默认值同源；公网发布应靠凭证下发 wsUrl 或同源反代，不依赖此常量。
 */
const IM_WS_FALLBACK = `ws://192.168.20.133:${IM_WS_PORT}`;

/** 按页面/接口协议选择 ws/wss：https 页面必须 wss，否则浏览器按混合内容拦截 */
function wsSchemeOf(httpProtocol: string): string {
  return httpProtocol === "https:" ? "wss:" : "ws:";
}

/**
 * WK ws 地址：优先用服务端下发的 wsUrl，缺省时按 BASE_URL / 当前页面主机推导。
 *
 * 关键约束：任何分支都不得回退 127.0.0.1。H5 生产 BASE_URL 是同源相对路径 "/api"，
 * 手机/平板上的 127.0.0.1 指向设备自身（其上没有 IM 服务），落过去连接必败且无日志，
 * 表现为消息永久红点。因此相对地址改由当前页面主机推导，极端情况用部署常量兜底。
 */
function wsAddr(wsUrl?: string): string {
  if (wsUrl) {
    return wsUrl;
  }
  // 1) 绝对 BASE_URL（dev / App / 小程序）：直接取其主机名推导
  try {
    const url = new URL(BASE_URL);
    return `${wsSchemeOf(url.protocol)}//${url.hostname}:${IM_WS_PORT}`;
  } catch {
    // 相对路径（H5 生产 "/api"）无法被 new URL 解析，落入下方页面主机推导
  }
  // 2) H5：用当前页面主机（nginx 与 IM 同宿主部署），协议随页面 http/https
  if (typeof window !== "undefined" && window.location?.hostname) {
    return `${wsSchemeOf(window.location.protocol)}//${window.location.hostname}:${IM_WS_PORT}`;
  }
  // 3) 非 H5 且 BASE_URL 异常的极端兜底：部署常量，保证永不落到 127.0.0.1
  return IM_WS_FALLBACK;
}

/**
 * 由 WS 地址推导 WuKongIM HTTP API 基址：
 * ws(s)://host:5200 → http(s)://host:5001（会话/历史同步回调走 HTTP API）
 */
function imApiBase(wsUrl?: string): string {
  try {
    const url = new URL(wsUrl || wsAddr());
    url.protocol = url.protocol === "wss:" ? "https:" : "http:";
    if (url.port === "5200") {
      url.port = "5001";
    }
    return url.origin;
  } catch {
    return "http://192.168.20.133:5001";
  }
}

/** UTF-8 字节数组转字符串（手写解码，兼容 App/小程序无 TextDecoder 的环境） */
function utf8BytesToString(bytes: Uint8Array): string {
  let out = "";
  let i = 0;
  while (i < bytes.length) {
    const b1 = bytes[i++];
    if (b1 < 0x80) {
      out += String.fromCharCode(b1);
      continue;
    }
    const b2 = bytes[i++] & 0x3f;
    if (b1 < 0xe0) {
      out += String.fromCharCode(((b1 & 0x1f) << 6) | b2);
      continue;
    }
    const b3 = bytes[i++] & 0x3f;
    if (b1 < 0xf0) {
      out += String.fromCharCode(((b1 & 0x0f) << 12) | (b2 << 6) | b3);
      continue;
    }
    const b4 = bytes[i++] & 0x3f;
    let cp = ((b1 & 0x07) << 18) | (b2 << 12) | (b3 << 6) | b4;
    cp -= 0x10000;
    out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
  }
  return out;
}

/** WuKongIM API 返回的 payload（Base64）解码为 UTF-8 文本 */
function decodePayloadText(base64: string): string {
  if (!base64) return "";
  try {
    return utf8BytesToString(new Uint8Array(uni.base64ToArrayBuffer(base64)));
  } catch {
    return "";
  }
}

/** WuKongIM 原始消息 JSON 转 SDK Message（content 还原为 MessageText，与实时消息路径一致） */
function rawToSdkMessage(raw: Record<string, unknown>, channel: Channel): Message {
  const msg = new Message();
  msg.channel = channel;
  msg.messageID = String(raw.message_id ?? "");
  msg.messageSeq = Number(raw.message_seq ?? 0);
  msg.clientMsgNo = String(raw.client_msg_no ?? "");
  msg.fromUID = String(raw.from_uid ?? "");
  msg.timestamp = Number(raw.timestamp ?? 0); // WuKongIM 时间戳为秒
  msg.content = new MessageText(unwrapBusinessText(decodePayloadText(String(raw.payload ?? ""))));
  return msg;
}

/**
 * 历史消息 payload 有两种形态：
 * 1) SDK 发送：{"content":"<业务 JSON>","type":1}（type 为数字，业务 JSON 被 content 包了一层）
 * 2) 直连 API：{"type":"image","url":...}（业务 JSON 即 payload 本身）
 * 这里统一取出业务 JSON 文本，否则第 1 种会被当成文本消息、原样显示成 JSON 字符串。
 */
function unwrapBusinessText(payloadText: string): string {
  try {
    const parsed = JSON.parse(payloadText);
    if (parsed && typeof parsed.type === "number" && typeof parsed.content === "string") {
      return parsed.content;
    }
  } catch {
    // 普通文本，非 JSON
  }
  return payloadText;
}

/** 以 Promise 封装 uni.request */
function postJson<T>(url: string, data: Record<string, unknown>): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method: "POST",
      data,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data as T);
        } else {
          reject(new Error(`WuKongIM HTTP ${res.statusCode}`));
        }
      },
      fail: (err) => reject(err),
    });
  });
}

/**
 * 注入 SDK 数据同步回调（会话列表 + 频道历史消息，均走 WuKongIM HTTP API）
 * wukongimjssdk 不内置 HTTP 实现，不注入则 conversationManager.sync()/syncMessages 直接抛错
 */
function setupProvider(apiBase: string): void {
  const sdk = WKSDK.shared();
  const uid = () => sdk.config.uid;

  // 最近会话列表：POST /conversation/sync，msg_count=1 携带最后一条消息
  sdk.config.provider.syncConversationsCallback = async (): Promise<Conversation[]> => {
    const list = await postJson<Record<string, unknown>[]>(`${apiBase}/conversation/sync`, {
      uid: uid(),
      msg_count: 1,
    });
    return (Array.isArray(list) ? list : [])
      .filter((raw) => Number(raw.channel_type) === ChannelTypePerson)
      .map((raw) => {
        const channel = sdk.newChannel(String(raw.channel_id ?? ""), ChannelTypePerson);
        const conversation = sdk.conversationManager.createEmptyConversation(channel);
        const recents = Array.isArray(raw.recents) ? raw.recents : [];
        if (recents.length > 0) {
          const last = recents[0] as Record<string, unknown>;
          conversation.lastMessage = rawToSdkMessage(last, channel);
          conversation.timestamp = Number(last.timestamp ?? 0);
        }
        return conversation;
      });
  };

  // 频道历史消息：POST /channel/messagesync，pullMode=Up + seq=0 由服务端返回最新一页
  // 响应为 {start_message_seq, end_message_seq, more, messages:[...]} 包装对象
  sdk.config.provider.syncMessagesCallback = async (channel, opts): Promise<Message[]> => {
    const resp = await postJson<{ messages?: Record<string, unknown>[] }>(
      `${apiBase}/channel/messagesync`,
      {
        login_uid: uid(),
        channel_id: channel.channelID,
        channel_type: channel.channelType,
        limit: opts.limit || 20,
        pull_mode: opts.pullMode ?? PullMode.Up,
        start_message_seq: opts.startMessageSeq || 0,
        end_message_seq: opts.endMessageSeq || 0,
      }
    );
    return (Array.isArray(resp?.messages) ? resp.messages : []).map((raw) =>
      rawToSdkMessage(raw, channel)
    );
  };
}

/**
 * 发起连接并等待鉴权结果（不再"connect 完即视为在线"）
 * @returns 超时时间内进入 Connected 返回 true；ConnectFail 或 5s 超时返回 false
 */
function connectAndWait(): Promise<boolean> {
  const sdk = WKSDK.shared();
  const connectManager = sdk.connectManager;
  return new Promise((resolve) => {
    let settled = false;
    const finish = (ok: boolean): void => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      connectManager.removeConnectStatusListener(listener);
      resolve(ok);
    };
    const timer = setTimeout(() => {
      // 5s 无终态：多为网络不可达/WS 地址错误，输出脱敏诊断（仅连接状态与地址，不含 token/消息）
      warnAlways(`[wukongim] 连接超时未就绪: connected=${connectManager.connected()}, addr=${sdk.config.addr}`);
      finish(connectManager.connected());
    }, 5000);
    const listener = (status: ConnectStatus, reasonCode?: number): void => {
      if (status === ConnectStatus.Connected) {
        finish(true);
      } else if (status === ConnectStatus.ConnectFail) {
        // reasonCode：1=success 2=authFail 3=subscriberNotExist 4=inBlacklist，0/缺省=unknown
        warnAlways(`[wukongim] 连接失败: reasonCode=${reasonCode ?? 0}, addr=${sdk.config.addr}`);
        finish(false);
      }
    };
    connectManager.addConnectStatusListener(listener);
    if (connectManager.connected()) {
      finish(true);
      return;
    }
    sdk.connect();
  });
}

/** 被踢下线监听：同账号在其他端登录后重置本地状态，下次 ensureSelfIM 可重新连接 */
function registerConnectionListener(): void {
  WKSDK.shared().connectManager.addConnectStatusListener((status: ConnectStatus) => {
    if (status === ConnectStatus.ConnectKick) {
      isLoggedIn = false;
    }
  });
}

/**
 * 后端 WuKongIM 下发的数字 type 到业务 type 的映射。
 * 后端消息体形如 {"type":1,"content":"..."}（type 为数字），前端契约用字符串 type，
 * 此处统一归一化，避免数字 type 消息被当作原始 JSON 字符串透传显示。
 */
const NUMERIC_TYPE_MAP: Record<number, string> = {
  1: "text",
  2: "image",
  3: "voice",
  4: "location",
  5: "card",
  6: "match",
  7: "system",
};

/** 从原始消息解析匹配事件（JSON 文本 {"type":"match"|"type":6,...}），非匹配消息返回 null */
function extractMatchEvent(message: Message): MatchEvent | null {
  const payload = message.content;
  if (payload instanceof MessageText) {
    try {
      const parsed = JSON.parse(payload.text || "");
      if (parsed && typeof parsed === "object") {
        const rawType: unknown = parsed.type;
        const type = typeof rawType === "number" ? NUMERIC_TYPE_MAP[rawType] ?? "" : String(rawType ?? "");
        if (type === "match") {
          return {
            from: String(parsed.userId ?? message.fromUID ?? ""),
            nickname: String(parsed.nickname ?? ""),
            avatar: String(parsed.avatar ?? ""),
          };
        }
      }
    } catch {
      // 普通文本消息，非 JSON
    }
  }
  return null;
}

/** 从原始消息解析系统通知（JSON 文本 {"type":"system"|"type":7,...}），非系统通知返回 null */
function extractSystemNotice(message: Message): SystemNotice | null {
  const payload = message.content;
  if (payload instanceof MessageText) {
    try {
      const parsed = JSON.parse(payload.text || "");
      if (parsed && typeof parsed === "object") {
        const rawType: unknown = parsed.type;
        const type = typeof rawType === "number" ? NUMERIC_TYPE_MAP[rawType] ?? "" : String(rawType ?? "");
        if (type === "system") {
          return {
            category: String(parsed.category ?? "") as SystemNotice["category"],
            title: String(parsed.title ?? ""),
            content: String(parsed.content ?? ""),
            time: (message.timestamp || 0) * 1000,
          };
        }
      }
    } catch {
      // 普通文本消息，非 JSON
    }
  }
  return null;
}

/** 将 WK 原始消息转为业务消息（文本与 JSON 结构化契约），非业务消息返回 null */
function toReceivedMessage(message: Message): ReceivedMessage | null {
  const payload = message.content;
  if (!(payload instanceof MessageText)) {
    return null;
  }
  const text = payload.text || "";
  const from = String(message.fromUID || "");
  const time = (message.timestamp || 0) * 1000; // WK 时间戳为秒
  try {
    const parsed = JSON.parse(text);
    if (parsed && typeof parsed === "object") {
      // 归一化 type：字符串直接用；数字走映射表；未知 type 按文本兜底
      let rawType: unknown = parsed.type;
      if (typeof rawType === "number") {
        rawType = NUMERIC_TYPE_MAP[rawType] ?? "";
      }
      const type = typeof rawType === "string" ? rawType : "";
      switch (type) {
        case "match":
          return null; // 匹配事件单独分发
        case "system":
          return null; // 系统通知单独分发
        case "image":
          if (parsed.url) {
            return { from, content: String(parsed.url), type: "image", time };
          }
          break;
        case "voice":
          if (parsed.url) {
            return {
              from,
              content: String(parsed.url),
              type: "voice",
              extra: { duration: Number(parsed.duration) || 0 },
              time,
            };
          }
          break;
        case "location":
          if (parsed.latitude !== undefined && parsed.longitude !== undefined) {
            return {
              from,
              content: String(parsed.address || "位置"),
              type: "location",
              extra: { latitude: Number(parsed.latitude), longitude: Number(parsed.longitude) },
              time,
            };
          }
          break;
        case "card":
          if (parsed.userId) {
            return {
              from,
              content: String(parsed.nickname || "名片"),
              type: "card",
              extra: { userId: String(parsed.userId), avatar: String(parsed.avatar || "") },
              time,
            };
          }
          break;
        case "text":
          // 数字 type=1 或字符串 type="text"：取 content 字段作为纯文本
          return {
            from,
            content: String(parsed.content ?? text),
            type: "text",
            time,
          };
        default:
          break;
      }
    }
  } catch {
    // 普通文本
  }
  return { from, content: text, type: "text", time };
}

/** 全局消息监听：SDK 事件归一化后转发到 im-events 总线（先注册，connect 后由 SDK 触发） */
function registerMessageListener(): void {
  WKSDK.shared().chatManager.addMessageListener((message) => {
    const systemNotice = extractSystemNotice(message);
    if (systemNotice) {
      emitSystemNotice(systemNotice);
      return;
    }
    const matchEvent = extractMatchEvent(message);
    if (matchEvent) {
      emitMatch(matchEvent);
      return;
    }
    const biz = toReceivedMessage(message);
    if (biz) {
      // clientMsgNo 是发送端乐观气泡与回包合并的唯一键，必须在转发前带上
      biz.clientMsgNo = String(message.clientMsgNo || "");
      emitMessage(biz);
    }
  });
}

let listenerRegistered = false;

/**
 * 连接 WuKongIM（用户 ID + token）
 * 等待服务端鉴权结果后再返回；失败静默返回 false（聊天页按降级处理）
 */
export async function loginIM(
  userId: string,
  token: string,
  wsUrl?: string
): Promise<boolean> {
  if (isLoggedIn) {
    return true;
  }
  try {
    const sdk = WKSDK.shared();
    const addr = wsAddr(wsUrl);
    sdk.config.addr = addr;
    sdk.config.uid = userId;
    sdk.config.token = token;
    // 会话/历史 HTTP 同步回调（必须在首次 sync/syncMessages 前注入）
    setupProvider(imApiBase(addr));
    if (!listenerRegistered) {
      registerMessageListener();
      registerConnectionListener();
      listenerRegistered = true;
    }
    const ok = await connectAndWait();
    isLoggedIn = ok;
    return ok;
  } catch {
    isLoggedIn = false;
    return false;
  }
}

/**
 * 确保以"当前登录用户本人"身份接入 IM（所有业务页面统一入口）
 *
 * 历史问题：客服页曾以客服账号、好友聊天页曾以对方账号连接，导致顶号、消息 from_uid 错乱。
 * 本函数保证连接身份永远是当前用户：优先用后端下发凭证（盐值派生的真 token + wsUrl），
 * 网络失败时回退本地派生直连（开发环境 WuKongIM 未开 token 校验时兜底）。
 *
 * @returns IM 是否就绪
 */
export async function ensureSelfIM(): Promise<boolean> {
  // 已登录且底层连接存活：直接就绪
  if (isLoggedIn && isIMConnected()) {
    return true;
  }
  // 并发合并：App onShow、会话列表、聊天页可能同时触发，重复 connect 会互相顶号
  if (selfImTask) {
    return selfImTask;
  }
  selfImTask = connectSelfIM().finally(() => {
    selfImTask = null;
  });
  return selfImTask;
}

/**
 * ensureSelfIM 实现体：解析本人 uid 并完成登录。
 *
 * 冷启动/刷新时 pinia 里的 userInfo 必为空（只有 token 落盘），因此 uid 不能只从 store 取：
 * 优先用凭证端点下发的 username（服务端权威 uid，与 IM uid 同源），store 的 id 仅作兜底。
 * 这样任何入口（直接进消息页、刷新停在聊天页）都不必先访问「我的」预热用户信息。
 */
async function connectSelfIM(): Promise<boolean> {
  // isLoggedIn 仍为 true 但 WS 已断（弱网/被踢后自动重连失败）：必须重置后重走登录流程。
  // 否则 loginIM 的 isLoggedIn 短路会返回「就绪」假结果，消息在 sendMessagePayload 的
  // connected 判活处被永久拦下（SDK 断连期入队消息静默 shift 丢弃且不补发）。
  if (isLoggedIn) {
    isLoggedIn = false;
  }
  // 未登录快速失败：不轮询/不等待用户信息，交由路由守卫跳登录，避免页面卡在 loading
  if (!getToken()) {
    return false;
  }
  const credential = await getImCredential();
  const { useUserStore } = await import("@/store/user");
  const uid = String(credential?.username || useUserStore().userInfo?.id || "");
  if (!uid) {
    return false;
  }
  if (credential?.token) {
    const ok = await loginIM(uid, credential.token, credential.wsUrl);
    if (ok) {
      return true;
    }
  }
  return loginIMDirect(uid);
}

/**
 * WuKongIM 直连登录（无需后端 token 端点）
 * 前端按 userId 本地派生 token（与服务端 WuKongIM 自定义 token 模型保持一致）
 * 替代已废弃的 getHuanXinUser() + loginIM() 两步调用
 */
export async function loginIMDirect(userId: string): Promise<boolean> {
  const token = `xfjy_${userId}_im_token`;
  // 显式传入健壮推导的 WS 地址：凭证端点降级走直连时，也不能让 wsAddr 落到 127.0.0.1
  return loginIM(userId, token, wsAddr());
}

/** 断开连接并重置状态 */
export async function logoutIM(): Promise<void> {
  try {
    WKSDK.shared().disconnect();
  } catch {
    // 静默
  }
  isLoggedIn = false;
}

/** 是否已登录（模块级登录态：仅登录成功/被踢下线时翻转，不随弱网断连翻转） */
export function isIMLoggedIn(): boolean {
  return isLoggedIn;
}

/**
 * 当前 IM 登录身份（uid）：未连接时返回空串。
 * 供冷启动场景判断消息归属——此路径不依赖 pinia userInfo，刷新后同样可用。
 */
export function getSelfIMUid(): string {
  try {
    return String(WKSDK.shared().config.uid || "");
  } catch {
    return "";
  }
}

/**
 * WebSocket 实际连接状态（与 isIMLoggedIn 语义不同，禁止合并）。
 *
 * isLoggedIn 不随网络断开翻转；而 SDK 在 ws 为 null 时会把 sendPacketQueue
 * 中的消息每 100ms 静默 shift 丢弃，不抛错且重连后不补发。因此发送前必须
 * 以本函数判活，不能只信 isIMLoggedIn()。
 * SDK 未初始化等异常一律按未连接处理（保守失败，让消息走 failed 红点重发链路）。
 */
export function isIMConnected(): boolean {
  try {
    return WKSDK.shared().connectManager.connected();
  } catch (err) {
    warnAlways(`[wukongim] connected() 判活异常，按未连接处理: ${String(err)}`);
    return false;
  }
}

/**
 * 获取单聊会话列表，映射为展示用摘要
 */
export async function getConversations(): Promise<ChatSession[]> {
  if (!isLoggedIn) {
    return [];
  }
  try {
    const items = await WKSDK.shared().conversationManager.sync();
    return (items || [])
      .filter((c) => c.channel?.channelType === ChannelTypePerson)
      .map((c) => {
        // 复用实时消息同一套归一化：把 {"type":1,"content":"..."} 还原为纯文本，
        // 避免会话列表最后一条裸显原始 JSON；match/system 事件不进业务消息，给语义化兜底
        let lastMessage = "";
        if (c.lastMessage) {
          const received = toReceivedMessage(c.lastMessage);
          if (received) {
            lastMessage = received.content;
          } else if (extractMatchEvent(c.lastMessage)) {
            lastMessage = "匹配成功";
          } else if (extractSystemNotice(c.lastMessage)) {
            lastMessage = "系统通知";
          }
        }
        return {
          conversationId: String(c.channel?.channelID || ""),
          lastMessage,
          lastTime: (c.timestamp || 0) * 1000,
          unreadCount: c.unread || 0,
        };
      });
  } catch {
    return [];
  }
}

/** 发送回执等待上限：超时即判失败，避免气泡永久停在「发送中」 */
const SEND_ACK_TIMEOUT = 10000;

/** 发送回执成功码：WuKongIM 用 reasonCode=1 表示成功（0 为旧版/默认值，一并视为成功） */
const SEND_ACK_SUCCESS = new Set([0, 1]);

/** 等待指定 clientSeq 的发送回执（SendackPacket），reasonCode 为成功码才算服务端已接收 */
function waitSendAck(clientSeq: number, clientMsgNo?: string): Promise<boolean> {
  return new Promise((resolve) => {
    let settled = false;
    const finish = (ok: boolean) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      WKSDK.shared().chatManager.removeMessageStatusListener(listener);
      resolve(ok);
    };
    const listener = (ack: SendackPacket) => {
      if (ack.clientSeq !== clientSeq) return;
      const ok = SEND_ACK_SUCCESS.has(ack.reasonCode);
      if (!ok) {
        warnAlways(
          `[wukongim] 发送回执失败: clientMsgNo=${clientMsgNo ?? ""}, clientSeq=${clientSeq}, reasonCode=${ack.reasonCode}`
        );
      }
      finish(ok);
    };
    const timer = setTimeout(() => {
      warnAlways(`[wukongim] 发送回执超时: clientMsgNo=${clientMsgNo ?? ""}, clientSeq=${clientSeq}`);
      // 移出发送队列：避免重连时 SDK 自动补发，造成「界面显示失败但实际已投递」
      WKSDK.shared().chatManager.sendingQueues.delete(clientSeq);
      finish(false);
    }, SEND_ACK_TIMEOUT);
    WKSDK.shared().chatManager.addMessageStatusListener(listener);
  });
}

/**
 * 发送单聊文本消息（含 JSON 结构化契约）
 * @param clientMsgNo 调用方生成的客户端消息号：本地乐观气泡与回包/历史按它合并去重，重发复用同一个
 */
async function sendMessagePayload(to: string, text: string, clientMsgNo?: string): Promise<boolean> {
  if (!isLoggedIn) {
    return false;
  }
  // 发送前判活：断连期间 SDK 入队后静默丢弃且重连不补发，
  // 未连接直接返回 false，不进 SDK 队列，让气泡走现有 failed 红点 + 点击重发链路。
  // 文本内容可能含隐私，日志只记录投递目标 uid，不打印 text。
  if (!isIMConnected()) {
    warnAlways(`[wukongim] 发送中止：IM WebSocket 未连接，to=${to}`);
    return false;
  }
  try {
    const sdk = WKSDK.shared();
    const chatManager = sdk.chatManager;
    const channel = sdk.newChannel(to, ChannelTypePerson);
    const content = sdk.newMessageText(text);
    // 走 getSendPacket 而非 send()：SDK 的 send() 自造 clientMsgNo 且立即 resolve，
    // 外部无从关联回执；这里自持 clientMsgNo + 等 SendackPacket 才算发送成功
    const packet = chatManager.getSendPacket(content, channel);
    if (clientMsgNo) {
      packet.clientMsgNo = clientMsgNo;
    }
    const acked = waitSendAck(packet.clientSeq, clientMsgNo);
    // 入发送队列：断线重连时 SDK flushSendingQueue 会补发未回执的消息
    chatManager.sendingQueues.set(packet.clientSeq, packet);
    chatManager.sendSendPacket(packet);
    // 保留 SDK 原有的「本地已入队」通知（会话列表/未读等既有订阅方依赖）
    chatManager.notifyMessageListeners(Message.fromSendPacket(packet, content));
    return await acked;
  } catch (err) {
    // catch 不得空吞：记录投递目标与异常上下文，返回 false 由消费方置 failed
    warnAlways(`[wukongim] 消息发送异常，to=${to}, err=${String(err)}`);
    return false;
  }
}

/** 发送文本消息（单聊） */
export async function sendTextMessage(
  to: string,
  content: string,
  clientMsgNo?: string
): Promise<boolean> {
  return sendMessagePayload(to, content, clientMsgNo);
}

/** 发送结构化消息（单聊，JSON 文本承载：image/voice/location/card 等） */
export async function sendStructuredMessage(
  to: string,
  payload: Record<string, unknown>,
  clientMsgNo?: string
): Promise<boolean> {
  return sendMessagePayload(to, JSON.stringify(payload), clientMsgNo);
}

/** 发送图片消息（单聊，MinIO URL 以 JSON 文本承载） */
export async function sendImageMessage(to: string, url: string): Promise<boolean> {
  return sendStructuredMessage(to, { type: "image", url });
}

/**
 * 拉取单聊历史消息（最新一页，按时间正序）
 * 经 SDK 的 syncMessages → syncMessagesCallback → WuKongIM HTTP /channel/messagesync，
 * 与实时消息处于同一频道空间（channel_id=对方 uid），历史与实时天然连续。
 */
export async function getChatHistory(
  conversationId: string,
  pageSize = 20
): Promise<ReceivedMessage[]> {
  if (!isLoggedIn || !conversationId) {
    return [];
  }
  try {
    const sdk = WKSDK.shared();
    const channel = sdk.newChannel(conversationId, ChannelTypePerson);
    const opts = new SyncOptions();
    opts.startMessageSeq = 0;
    opts.endMessageSeq = 0;
    opts.limit = pageSize;
    opts.pullMode = PullMode.Up; // 上拉模式 + seq=0：服务端返回最新一页
    const messages = await sdk.chatManager.syncMessages(channel, opts);
    return (messages || [])
      .map((raw) => {
        const m = toReceivedMessage(raw);
        if (m) {
          m.seq = raw.messageSeq || 0;
          m.clientMsgNo = String(raw.clientMsgNo || "");
        }
        return m;
      })
      .filter((m): m is ReceivedMessage => m !== null)
      .sort((a, b) => (a.time || 0) - (b.time || 0));
  } catch (err) {
    // 不再伪装成空数组：空数组语义是「真的没有历史消息」，失败必须让调用方感知并展示错误空态。
    // 记录会话与异常上下文后重抛，由聊天页内层 try/catch 置失败标记；前置条件分支仍返回 []。
    warn(`[wukongim] 历史消息拉取失败，conversationId=${conversationId}, err=${String(err)}`);
    throw err;
  }
}

/**
 * 续拉更旧的历史消息（scrolltoupper 翻页，M-K4）。
 * 从 beforeSeq 向下拉（PullMode.Down），返回 seq < beforeSeq 的最多 pageSize 条，按时间正序。
 * beforeSeq 非法（≤0）或未登录时返回空数组，调用方据此置「没有更多」。
 */
export async function getChatHistoryBefore(
  conversationId: string,
  beforeSeq: number,
  pageSize = 20
): Promise<ReceivedMessage[]> {
  if (!isLoggedIn || !conversationId || beforeSeq <= 0) {
    return [];
  }
  try {
    const sdk = WKSDK.shared();
    const channel = sdk.newChannel(conversationId, ChannelTypePerson);
    const opts = new SyncOptions();
    opts.startMessageSeq = beforeSeq;
    opts.endMessageSeq = 0;
    opts.limit = pageSize;
    opts.pullMode = PullMode.Down; // 向下拉更旧消息
    const messages = await sdk.chatManager.syncMessages(channel, opts);
    return (messages || [])
      .map((raw) => {
        const m = toReceivedMessage(raw);
        if (m) {
          m.seq = raw.messageSeq || 0;
          m.clientMsgNo = String(raw.clientMsgNo || "");
        }
        return m;
      })
      .filter((m): m is ReceivedMessage => m !== null)
      .sort((a, b) => (a.time || 0) - (b.time || 0));
  } catch (err) {
    warn(`[wukongim] 历史消息续拉失败，conversationId=${conversationId}, beforeSeq=${beforeSeq}, err=${String(err)}`);
    throw err;
  }
}
