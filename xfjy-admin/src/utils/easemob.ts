/**
 * 环信 IM SDK 封装（admin 工作台版，easemob-websdk 5.x）
 * 逻辑与 app 端 src/utils/easemob.ts 对齐，凭证由后端 /admin/kefu/agent/credential 下发
 */

import {
  ChatClient,
  type Message,
  type MessageType,
  type ConversationItem,
  type TextMessageBody,
} from "easemob-websdk";

/** 环信应用标识（客户端公开配置，非密钥；密钥 client-id/secret 仅存后端） */
const EASEMOB_APP_KEY = "1105201102042781#xfjy";

/** 事件处理器唯一标识 */
const HANDLER_ID = "xfjy-admin";

/** 会话摘要（工作台会话列表展示用） */
export interface ChatSession {
  /** 会话对方环信 ID（即业务用户 ID 字符串） */
  conversationId: string;
  /** 最后一条消息文本 */
  lastMessage: string;
  /** 最后消息时间戳（毫秒） */
  lastTime: number;
  /** 未读消息数 */
  unreadCount: number;
}

/** 收到的消息（文本/图片/语音等；非文本消息 content 为占位描述，body 保留原始消息体供渲染） */
export interface ReceivedMessage {
  /** 发送方环信 ID */
  from: string;
  /** 消息内容（文本消息为正文；非文本消息为 [图片]/[语音] 等占位描述） */
  content: string;
  /** 消息类型（text/image/voice/video/file/location/custom/cmd/combine） */
  type: MessageType;
  /** 原始消息体（图片消息含 localUrl/thumbnailUrl 等候选地址，语音消息含 url/duration） */
  body: Message["body"];
  /** 环信服务端消息 ID（已读回执匹配用；历史消息与实时消息均携带） */
  msgId: string;
  /** 时间戳（毫秒） */
  time: number;
}

/** 消息到达回调类型 */
type MessageHandler = (msg: ReceivedMessage) => void;

/** 全局消息回调列表 */
const messageHandlers: MessageHandler[] = [];

/**
 * SDK manager mixin 方法签名（ChatClient 经 WithManagers 混入，TS 严格模式下
 * 推断为不可调用的 ManagerBase，需断言后调用）
 */
interface ChatClientManagers {
  createTextMessage(opts: {
    conversationId: string;
    conversationType: "singleChat";
    content: string;
  }): Message;
  getHistoryMessages(opts: {
    conversationId: string;
    conversationType: "singleChat";
    pageSize: number;
    searchDirection: "up";
  }): Promise<{ items: Message[] }>;
}

/**
 * 环信客户端单例
 * SDK 5.x 的 WithManagers 泛型在 TS 严格模式下推断异常（init 返回值不可赋值、
 * manager 方法不可调用），统一收敛为 ChatClient 类型，调用处局部断言绕过。
 */
let client: ChatClient | null = null;

/** 登录状态 */
let isLoggedIn = false;

/**
 * 从消息体提取展示文本：文本消息返回正文，非文本消息返回占位描述
 * （占位描述保证图片/语音等消息不会被过滤掉，同时避免展示空气泡）
 */
function extractText(message: { type?: MessageType | undefined; body: unknown }): string {
  if (message.type === "text") {
    return String((message.body as TextMessageBody).content ?? "");
  }
  switch (message.type) {
    case "image":
      return "[图片]";
    case "voice":
      return "[语音]";
    case "video":
      return "[视频]";
    case "file":
      return "[文件]";
    case "location":
      return "[位置]";
    default:
      return "[消息]";
  }
}

/**
 * 初始化环信 SDK（单例，幂等）
 */
function getClient(): ChatClient {
  if (client) {
    return client;
  }
  client = ChatClient.init({
    appKey: EASEMOB_APP_KEY,
  }) as unknown as ChatClient;
  client.addEventHandler(HANDLER_ID, {
    onMessage(message: Message) {
      const received: ReceivedMessage = {
        from: message.from,
        content: extractText(message),
        type: message.type,
        body: message.body,
        msgId: message.msgServerId,
        time: message.timestamp,
      };
      messageHandlers.forEach((handler) => handler(received));
    },
    onMessageReadReceipts(event: any) {
      // 单聊已读回执：仅处理 singleChat 类型，载荷含 conversationId/messageIds/timestamp
      if (event?.conversationType === 'singleChat') {
        readReceiptHandlers.forEach((handler) => handler({
          conversationId: event.conversationId,
          messageIds: [...event.messageIds],
          timestamp: event.timestamp,
        }));
      }
    },
  });
  return client;
}

/**
 * 登录环信（用户 ID + token 方式）
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
 * 获取环信会话列表（仅单聊）
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
        lastMessage: item.lastMessage ? extractText(item.lastMessage) : "",
        lastTime: item.lastMessageAt || item.lastMessage?.timestamp || 0,
        unreadCount: item.unreadCount || 0,
      }));
  } catch {
    return [];
  }
}

/**
 * 注册消息到达回调
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

/** 已读回执回调载荷（单聊：{conversationId, messageIds, timestamp}） */
export interface ReadReceiptPayload {
  /** 会话 ID */
  conversationId: string;
  /** 已读的消息 ID 列表（服务端消息 ID，即 msgServerId） */
  messageIds: string[];
  /** 回执时间戳（毫秒） */
  timestamp: number;
}

/** 已读回执回调类型 */
type ReadReceiptHandler = (payload: ReadReceiptPayload) => void;

/** 全局已读回执回调列表 */
const readReceiptHandlers: ReadReceiptHandler[] = [];

/**
 * 注册消息已读回执回调（单聊：对方已读客服发送的消息时触发）
 * @returns 取消注册函数
 */
export function onMessageReadReceipts(handler: ReadReceiptHandler): () => void {
  readReceiptHandlers.push(handler);
  return () => {
    const index = readReceiptHandlers.indexOf(handler);
    if (index >= 0) {
      readReceiptHandlers.splice(index, 1);
    }
  };
}

/**
 * 发送文本消息（单聊）
 * @returns 发送成功返回消息对象（含 msgServerId，用于已读回执匹配）；失败返回 null
 */
export async function sendTextMessage(
  to: string,
  content: string
): Promise<Message | null> {
  if (!isLoggedIn) {
    return null;
  }
  try {
    const connection = getClient();
    const message = (connection as unknown as ChatClientManagers).createTextMessage({
      conversationId: to,
      conversationType: "singleChat",
      content,
    });
    await connection.sendMessage(message);
    return message;
  } catch {
    return null;
  }
}

/**
 * 拉取单聊历史消息（从服务端，向上翻页）
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
    const page = await (connection as unknown as ChatClientManagers).getHistoryMessages({
      conversationId,
      conversationType: "singleChat",
      pageSize,
      searchDirection: "up",
    });
    return page.items
      .map((msg) => ({
        from: msg.from,
        content: extractText(msg),
        type: msg.type,
        body: msg.body,
        msgId: msg.msgServerId,
        time: msg.timestamp,
      }))
      .sort((a, b) => a.time - b.time);
  } catch {
    return [];
  }
}

/**
 * 获取环信登录状态
 */
export function isEasemobLoggedIn(): boolean {
  return isLoggedIn;
}