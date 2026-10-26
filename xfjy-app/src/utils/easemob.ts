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
  /** 消息内容 */
  content: string;
  /** 时间戳（毫秒） */
  time: number;
}

/** 消息到达回调类型 */
type MessageHandler = (msg: ReceivedMessage) => void;

/** 全局消息回调列表 */
const messageHandlers: MessageHandler[] = [];

/** 环信客户端单例 */
let client: ReturnType<typeof ChatClient.init> | null = null;

/** 登录状态 */
let isLoggedIn = false;

/**
 * 从消息体提取文本内容
 * @param message 环信消息
 * @returns 文本内容，非文本消息返回空串
 */
function extractText(message: Message): string {
  if (message.type === "text") {
    return (message.body as TextMessageBody).content;
  }
  return "";
}

/**
 * 初始化环信 SDK（单例，幂等）
 * @returns 环信客户端实例
 */
function getClient(): ReturnType<typeof ChatClient.init> {
  if (client) {
    return client;
  }
  client = ChatClient.init({
    appKey: EASEMOB_APP_KEY,
  });
  client.addEventHandler(GLOBAL_HANDLER_ID, {
    /** 收到消息 */
    onMessage(message: Message) {
      const received: ReceivedMessage = {
        from: message.from,
        content: extractText(message),
        time: message.timestamp,
      };
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
      .map((msg) => ({
        from: msg.from,
        content: extractText(msg),
        time: msg.timestamp,
      }))
      .filter((msg) => msg.content !== "")
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