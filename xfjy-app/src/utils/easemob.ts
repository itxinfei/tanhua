/**
 * 环信 IM SDK 封装（easemob-websdk 5.x）
 * 负责初始化、登录、会话列表获取、消息事件分发
 * 凭证由后端 /huanxin/user 接口下发（用户级 token），前端不持久化环信密码/token
 */

import {
  ChatClient,
  type Message,
  type ConversationItem,
  type TextMessageBody,
} from "easemob-websdk";

/** 环信应用标识（客户端公开配置，非密钥；密钥 client-id/secret 仅存后端） */
const EASEMOB_APP_KEY = "1105201102042781#xfjy";

/** 事件处理器唯一标识 */
const GLOBAL_HANDLER_ID = "xfjy-global";

/** 会话摘要（聊天列表展示用） */
export interface ChatSession {
  /** 会话对方环信 ID */
  conversationId: string;
  /** 最后一条消息文本 */
  lastMessage: string;
  /** 最后消息时间戳（毫秒） */
  lastTime: number;
  /** 未读消息数 */
  unreadCount: number;
}

/** 收到的文本消息 */
export interface ReceivedMessage {
  /** 发送方环信 ID */
  from: string;
  /** 消息内容：文本内容 / 图片或语音 URL / 位置地址文本 */
  content: string;
  /** 消息类型 */
  type: "text" | "image" | "voice" | "location" | "card";
  /** 结构化附加字段（voice.duration / location 经纬度 / card 用户信息等） */
  extra?: Record<string, any>;
  /** 时间戳（毫秒） */
  time: number;
}

/** 匹配成功事件（后端互喜时经环信透传下发） */
export interface MatchEvent {
  /** 发起方（滑我的人）业务用户 ID */
  from: string;
  /** 发起方昵称 */
  nickname: string;
  /** 发起方头像 */
  avatar: string;
}

/** 消息到达回调类型 */
type MessageHandler = (msg: ReceivedMessage) => void;

/** 匹配事件回调类型 */
type MatchHandler = (event: MatchEvent) => void;

/** 全局消息回调列表 */
const messageHandlers: MessageHandler[] = [];

/** 全局匹配事件回调列表 */
const matchHandlers: MatchHandler[] = [];

/**
 * 环信客户端方法签名收敛接口
 * SDK 的 ChatClient.init 返回类型推断为联合类型（ManagerBase<ChatClient> | ...），
 * 导致 createTextMessage / getHistoryMessages 等方法在类型层面不可调用（TS2349），
 * 此处按实际使用的方法收敛声明，初始化时统一断言，不影响运行时行为
 */
interface EasemobClient {
  addEventHandler(id: string, handlers: { onMessage?: (message: Message) => void }): void;
  login(params: { userId: string; token: string }): Promise<unknown>;
  logout(): Promise<unknown>;
  refreshSessionList(params: { includeEmpty: boolean }): Promise<ConversationItem[]>;
  createTextMessage(params: {
    conversationId: string;
    conversationType: string;
    content: string;
  }): Message;
  sendMessage(message: Message): Promise<unknown>;
  getHistoryMessages(params: {
    conversationId: string;
    conversationType: string;
    pageSize: number;
    searchDirection: string;
  }): Promise<{ items: Message[] }>;
}

/** 环信客户端单例 */
let client: EasemobClient | null = null;

/** 登录状态 */
let isLoggedIn = false;

/**
 * 从消息中提取匹配事件（非匹配消息返回 null）
 * 兼容两种形态：custom 消息（customExts 携带字段）与 JSON 文本消息（content 为 {type:"match",...}）
 */
function extractMatchEvent(message: Message): MatchEvent | null {
  if (message.type === "custom") {
    const body = message.body as Record<string, any>;
    const ext = body?.customExts ?? body;
    if (ext && String(ext.type) === "match") {
      return {
        from: String(ext.userId ?? message.from ?? ""),
        nickname: String(ext.nickname ?? ""),
        avatar: String(ext.avatar ?? ""),
      };
    }
    return null;
  }
  if (message.type === "text") {
    const content = (message.body as TextMessageBody).content;
    try {
      const parsed = JSON.parse(content);
      if (parsed && parsed.type === "match") {
        return {
          from: String(parsed.userId ?? message.from ?? ""),
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

/**
 * 将环信消息转为业务消息（非文本类消息返回 null）
 * 结构化消息契约：JSON 文本 {"type":"image|voice|location|card", ...}（媒体本体存 MinIO，环信只传 URL）
 */
function toReceivedMessage(message: Message): ReceivedMessage | null {
  if (message.type !== "text") {
    return null;
  }
  const content = (message.body as TextMessageBody).content;
  try {
    const parsed = JSON.parse(content);
    if (parsed && typeof parsed.type === "string") {
      switch (parsed.type) {
        case "image":
          if (parsed.url) {
            return { from: message.from, content: String(parsed.url), type: "image", time: message.timestamp };
          }
          break;
        case "voice":
          if (parsed.url) {
            return {
              from: message.from,
              content: String(parsed.url),
              type: "voice",
              extra: { duration: Number(parsed.duration) || 0 },
              time: message.timestamp,
            };
          }
          break;
        case "location":
          if (parsed.latitude !== undefined && parsed.longitude !== undefined) {
            return {
              from: message.from,
              content: String(parsed.address || "位置"),
              type: "location",
              extra: { latitude: Number(parsed.latitude), longitude: Number(parsed.longitude) },
              time: message.timestamp,
            };
          }
          break;
        case "card":
          if (parsed.userId) {
            return {
              from: message.from,
              content: String(parsed.nickname || "名片"),
              type: "card",
              extra: { userId: String(parsed.userId), avatar: String(parsed.avatar || "") },
              time: message.timestamp,
            };
          }
          break;
      }
    }
  } catch {
    // 普通文本消息，非 JSON
  }
  return {
    from: message.from,
    content,
    type: "text",
    time: message.timestamp,
  };
}

/**
 * 初始化环信 SDK（单例，幂等）
 * @returns 环信客户端实例
 */
function getClient(): EasemobClient {
  if (client) {
    return client;
  }
  client = ChatClient.init({
    appKey: EASEMOB_APP_KEY,
  }) as unknown as EasemobClient;
  client.addEventHandler(GLOBAL_HANDLER_ID, {
    /** 收到消息 */
    onMessage(message: Message) {
      // 匹配事件单独分发，不进入文本消息流（避免 JSON 混入聊天记录）
      const matchEvent = extractMatchEvent(message);
      if (matchEvent) {
        matchHandlers.forEach((handler) => handler(matchEvent));
        return;
      }
      const received = toReceivedMessage(message);
      if (!received) return;
      messageHandlers.forEach((handler) => handler(received));
    },
  });
  return client;
}

/**
 * 登录环信（用户 ID + token 方式）
 * 登录失败静默降级（聊天列表退化为纯联系人展示）
 *
 * @param userId 环信用户 ID
 * @param token 环信用户级 token（由后端 /huanxin/user 接口换取下发）
 * @returns 登录是否成功
 */
export async function loginEasemob(
  userId: string,
  token: string
): Promise<boolean> {
  try {
    const connection = getClient();
    await connection.login({ userId, token });
    isLoggedIn = true;
    return true;
  } catch {
    isLoggedIn = false;
    return false;
  }
}

/**
 * 登出环信并重置状态
 */
export async function logoutEasemob(): Promise<void> {
  if (client && isLoggedIn) {
    await client.logout();
  }
  isLoggedIn = false;
}

/**
 * 获取环信会话列表
 * 仅返回单聊会话，映射为展示用摘要
 *
 * @returns 会话摘要列表
 */
export async function getConversations(): Promise<ChatSession[]> {
  if (!isLoggedIn) {
    return [];
  }
  try {
    const connection = getClient();
    const items: ReadonlyArray<ConversationItem> =
      await connection.refreshSessionList({ includeEmpty: false });
    return items
      .filter((item) => item.conversationType === "singleChat")
      .map((item) => ({
        conversationId: item.conversationId,
        lastMessage: item.lastMessage
          ? String((item.lastMessage.body as Record<string, unknown>)?.content || "")
          : "",
        lastTime: item.lastMessageAt || item.lastMessage?.timestamp || 0,
        unreadCount: item.unreadCount || 0,
      }));
  } catch {
    return [];
  }
}

/**
 * 注册匹配事件回调（互喜时后端经环信透传触发）
 * @param handler 回调函数
 * @returns 取消注册函数
 */
export function onMatchReceived(handler: MatchHandler): () => void {
  matchHandlers.push(handler);
  return () => {
    const index = matchHandlers.indexOf(handler);
    if (index >= 0) {
      matchHandlers.splice(index, 1);
    }
  };
}

/**
 * 注册消息到达回调
 * @param handler 回调函数
 * @returns 取消注册函数
 */
export function onMessageReceived(handler: MessageHandler): () => void {
  messageHandlers.push(handler);
  return () => {
    const index = messageHandlers.indexOf(handler);
    if (index >= 0) {
      messageHandlers.splice(index, 1);
    }
  };
}

/**
 * 发送文本消息（单聊）
 *
 * @param to 对方环信 ID（即对方业务用户 ID 字符串）
 * @param content 文本内容
 * @returns 发送是否成功
 */
export async function sendTextMessage(
  to: string,
  content: string
): Promise<boolean> {
  if (!isLoggedIn) {
    return false;
  }
  try {
    const connection = getClient();
    const message = connection.createTextMessage({
      conversationId: to,
      conversationType: "singleChat",
      content,
    });
    await connection.sendMessage(message);
    return true;
  } catch {
    return false;
  }
}

/**
 * 发送结构化消息（单聊，JSON 文本承载）
 * image/voice/location/card 等扩展消息统一走此管道
 *
 * @param to 对方环信 ID（即对方业务用户 ID 字符串）
 * @param payload 结构化负载，需含 type 字段
 * @returns 发送是否成功
 */
export async function sendStructuredMessage(
  to: string,
  payload: Record<string, unknown>
): Promise<boolean> {
  if (!isLoggedIn) {
    return false;
  }
  try {
    const connection = getClient();
    const message = connection.createTextMessage({
      conversationId: to,
      conversationType: "singleChat",
      content: JSON.stringify(payload),
    });
    await connection.sendMessage(message);
    return true;
  } catch {
    return false;
  }
}

/**
 * 发送图片消息（单聊，URL 形式）
 * 图片本体已上传 MinIO，环信仅传递 URL（JSON 文本承载）
 *
 * @param to 对方环信 ID（即对方业务用户 ID 字符串）
 * @param url 图片 MinIO 访问 URL
 * @returns 发送是否成功
 */
export async function sendImageMessage(to: string, url: string): Promise<boolean> {
  return sendStructuredMessage(to, { type: "image", url });
}

/**
 * 拉取单聊历史消息（从服务端，向上翻页）
 *
 * @param conversationId 对方环信 ID
 * @param pageSize 每页条数，默认 20
 * @returns 消息列表（按时间正序），登录失败或异常返回空数组
 */
export async function getChatHistory(
  conversationId: string,
  pageSize = 20
): Promise<ReceivedMessage[]> {
  if (!isLoggedIn) {
    return [];
  }
  try {
    const connection = getClient();
    const page = await connection.getHistoryMessages({
      conversationId,
      conversationType: "singleChat",
      pageSize,
      searchDirection: "up",
    });
    return page.items
      .map((msg) => toReceivedMessage(msg))
      .filter((msg): msg is ReceivedMessage => msg !== null && msg.content !== "")
      .sort((a, b) => a.time - b.time);
  } catch {
    return [];
  }
}

/**
 * 获取环信登录状态
 * @returns 是否已登录
 */
export function isEasemobLoggedIn(): boolean {
  return isLoggedIn;
}