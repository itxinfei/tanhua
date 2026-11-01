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
} from "wukongimjssdk";
import { BASE_URL } from "./request";

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

/** 收到的业务消息（已从 JSON 契约解析） */
export interface ReceivedMessage {
  from: string;
  content: string;
  type: "text" | "image" | "voice" | "location" | "card";
  extra?: Record<string, unknown>;
  time?: number;
}

/** 匹配事件（互喜时后端经 IM 透传触发） */
export interface MatchEvent {
  from: string;
  nickname: string;
  avatar: string;
}

type MessageHandler = (msg: ReceivedMessage) => void;
type MatchHandler = (event: MatchEvent) => void;

const messageHandlers: MessageHandler[] = [];
const matchHandlers: MatchHandler[] = [];

/** 登录状态 */
let isLoggedIn = false;

/**
 * WK ws 地址：优先用服务端下发的 wsUrl，缺省时从网关基地址推导（http://host:8090 -> ws://host:5200）
 */
function wsAddr(wsUrl?: string): string {
  if (wsUrl) {
    return wsUrl;
  }
  try {
    const url = new URL(BASE_URL);
    return `ws://${url.hostname}:5200`;
  } catch {
    return "ws://127.0.0.1:5200";
  }
}

/** 从原始消息解析匹配事件（JSON 文本 {"type":"match",...}），非匹配消息返回 null */
function extractMatchEvent(message: Message): MatchEvent | null {
  const payload = message.content;
  if (payload instanceof MessageText) {
    try {
      const parsed = JSON.parse(payload.text || "");
      if (parsed && parsed.type === "match") {
        return {
          from: String(parsed.userId ?? message.fromUID ?? ""),
          nickname: String(parsed.nickname ?? ""),
          avatar: String(parsed.avatar ?? ""),
        };
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
    if (parsed && typeof parsed.type === "string") {
      switch (parsed.type) {
        case "match":
          return null; // 匹配事件单独分发
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
        default:
          break;
      }
    }
  } catch {
    // 普通文本
  }
  return { from, content: text, type: "text", time };
}

/** 全局消息监听：分发给业务回调（先注册，connect 后由 SDK 触发） */
function registerMessageListener(): void {
  WKSDK.shared().chatManager.addMessageListener((message) => {
    const matchEvent = extractMatchEvent(message);
    if (matchEvent) {
      matchHandlers.forEach((h) => h(matchEvent));
      return;
    }
    const biz = toReceivedMessage(message);
    if (biz) {
      messageHandlers.forEach((h) => h(biz));
    }
  });
}

let listenerRegistered = false;

/**
 * 连接 WuKongIM（用户 ID + token）
 * 失败静默返回 false（聊天列表退化为纯联系人展示）
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
    sdk.config.addr = wsAddr(wsUrl);
    sdk.config.uid = userId;
    sdk.config.token = token;
    if (!listenerRegistered) {
      registerMessageListener();
      listenerRegistered = true;
    }
    sdk.connect();
    isLoggedIn = true;
    return true;
  } catch {
    isLoggedIn = false;
    return false;
  }
}

/**
 * WuKongIM 直连登录（无需后端 token 端点）
 * 前端按 userId 本地派生 token（与服务端 WuKongIM 自定义 token 模型保持一致）
 * 替代已废弃的 getHuanXinUser() + loginIM() 两步调用
 */
export async function loginIMDirect(userId: string): Promise<boolean> {
  const token = `xfjy_${userId}_im_token`;
  return loginIM(userId, token);
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

/** 是否已连接 */
export function isIMLoggedIn(): boolean {
  return isLoggedIn;
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
        const last = c.lastMessage?.content;
        const lastText =
          last instanceof MessageText ? last.text || "" : "";
        return {
          conversationId: String(c.channel?.channelID || ""),
          lastMessage: lastText,
          lastTime: (c.timestamp || 0) * 1000,
          unreadCount: c.unread || 0,
        };
      });
  } catch {
    return [];
  }
}

/** 注册匹配事件回调，返回取消注册函数 */
export function onMatchReceived(handler: MatchHandler): () => void {
  matchHandlers.push(handler);
  return () => {
    const i = matchHandlers.indexOf(handler);
    if (i >= 0) matchHandlers.splice(i, 1);
  };
}

/** 注册消息到达回调，返回取消注册函数 */
export function onMessageReceived(handler: MessageHandler): () => void {
  messageHandlers.push(handler);
  return () => {
    const i = messageHandlers.indexOf(handler);
    if (i >= 0) messageHandlers.splice(i, 1);
  };
}

/** 发送单聊文本消息（含 JSON 结构化契约） */
async function sendMessagePayload(to: string, text: string): Promise<boolean> {
  if (!isLoggedIn) {
    return false;
  }
  try {
    const sdk = WKSDK.shared();
    const channel = sdk.newChannel(to, ChannelTypePerson);
    await sdk.chatManager.send(sdk.newMessageText(text), channel);
    return true;
  } catch {
    return false;
  }
}

/** 发送文本消息（单聊） */
export async function sendTextMessage(to: string, content: string): Promise<boolean> {
  return sendMessagePayload(to, content);
}

/** 发送结构化消息（单聊，JSON 文本承载：image/voice/location/card 等） */
export async function sendStructuredMessage(
  to: string,
  payload: Record<string, unknown>
): Promise<boolean> {
  return sendMessagePayload(to, JSON.stringify(payload));
}

/** 发送图片消息（单聊，MinIO URL 以 JSON 文本承载） */
export async function sendImageMessage(to: string, url: string): Promise<boolean> {
  return sendStructuredMessage(to, { type: "image", url });
}

/**
 * 拉取单聊历史消息（签名与原环信封装一致）
 * 注意：wukongimjssdk 无本地历史查询 API，完整历史漫游需后端消息接口支持；
 * 当前返回空数组，聊天页加载历史为空后按无历史记录降级，实时收发不受影响。
 */
export async function getChatHistory(
  _conversationId: string,
  _pageSize = 20
): Promise<ReceivedMessage[]> {
  return [];
}
