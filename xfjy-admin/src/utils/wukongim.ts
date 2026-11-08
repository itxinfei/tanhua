/**
 * WuKongIM SDK 封装（admin 客服工作台版，wukongimjssdk 1.3.x）
 *
 * 背景：客服链路原接云环信（easemob-websdk），与 C 端自建 WuKongIM 双轨断裂、
 * 消息永远无法互通。本封装与 C 端 xfjy-app/src/utils/wukongim.ts 走同一通道、
 * 同一消息契约：纯文本直传；结构化消息以 JSON 文本承载
 * （{"type":"image|voice|location|card",...}，媒体本体存 MinIO，IM 只传 URL）。
 *
 * 数据同步（会话列表/历史消息）经 WuKongIM HTTP API（5001）回调注入，
 * 实时消息经 WebSocket（5200）。
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
} from 'wukongimjssdk'

/** 会话摘要（工作台会话列表展示用） */
export interface ChatSession {
  /** 会话对方 IM uid（C 端即业务用户 ID 字符串） */
  conversationId: string
  /** 最后一条消息文本（结构化消息为占位描述） */
  lastMessage: string
  /** 最后消息时间戳（毫秒） */
  lastTime: number
  /** 未读消息数 */
  unreadCount: number
}

/** 收到的业务消息（已从 JSON 契约解析） */
export interface ReceivedMessage {
  /** 发送方 IM uid */
  from: string
  /** 文本消息为正文；图片/语音为媒体 URL；位置为地址描述；名片为昵称 */
  content: string
  /** 消息类型 */
  type: 'text' | 'image' | 'voice' | 'location' | 'card'
  /** 结构化附加字段（语音 duration、位置经纬度、名片 userId/avatar） */
  extra?: Record<string, unknown>
  /** 频道内消息序号（本地 key 用） */
  messageSeq: number
  /** 时间戳（毫秒） */
  time: number
}

type MessageHandler = (msg: ReceivedMessage) => void

const messageHandlers: MessageHandler[] = []

/** 登录状态 */
let isLoggedIn = false
/** 监听器是否已注册（SDK 单例，只需一次） */
let listenerRegistered = false

/**
 * 由 WS 地址推导 WuKongIM HTTP API 基址：
 * ws(s)://host:5200 → http(s)://host:5001
 */
function imApiBase(wsUrl: string): string {
  try {
    const url = new URL(wsUrl)
    url.protocol = url.protocol === 'wss:' ? 'https:' : 'http:'
    if (url.port === '5200') {
      url.port = '5001'
    }
    return url.origin
  } catch {
    return 'http://192.168.20.133:5001'
  }
}

/** WuKongIM API 返回的 payload（Base64）解码为 UTF-8 文本（浏览器原生 API） */
function decodePayloadText(base64: string): string {
  if (!base64) return ''
  try {
    const binary = atob(base64)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i)
    }
    return new TextDecoder('utf-8').decode(bytes)
  } catch {
    return ''
  }
}

/** WuKongIM 原始消息 JSON 转 SDK Message（content 还原为 MessageText） */
function rawToSdkMessage(raw: Record<string, any>, channel: Channel): Message {
  const msg = new Message()
  msg.channel = channel
  msg.messageID = String(raw.message_id ?? '')
  msg.messageSeq = Number(raw.message_seq ?? 0)
  msg.clientMsgNo = String(raw.client_msg_no ?? '')
  msg.fromUID = String(raw.from_uid ?? '')
  msg.timestamp = Number(raw.timestamp ?? 0) // WuKongIM 时间戳为秒
  msg.content = new MessageText(decodePayloadText(String(raw.payload ?? '')))
  return msg
}

/**
 * 将 WK 原始消息转为业务消息（文本与 JSON 结构化契约），非业务消息返回 null
 * 与 C 端 xfjy-app wukongim.ts 的解析规则保持一致
 */
function toReceivedMessage(message: Message): ReceivedMessage | null {
  const payload = message.content
  if (!(payload instanceof MessageText)) {
    return null
  }
  const text = payload.text || ''
  const from = String(message.fromUID || '')
  const time = (message.timestamp || 0) * 1000 // WK 时间戳为秒
  const messageSeq = message.messageSeq || 0
  try {
    const parsed = JSON.parse(text)
    if (parsed && typeof parsed.type === 'string') {
      switch (parsed.type) {
        case 'image':
          if (parsed.url) {
            return { from, content: String(parsed.url), type: 'image', messageSeq, time }
          }
          break
        case 'voice':
          if (parsed.url) {
            return {
              from,
              content: String(parsed.url),
              type: 'voice',
              extra: { duration: Number(parsed.duration) || 0 },
              messageSeq,
              time,
            }
          }
          break
        case 'location':
          if (parsed.latitude !== undefined && parsed.longitude !== undefined) {
            return {
              from,
              content: String(parsed.address || '位置'),
              type: 'location',
              extra: { latitude: Number(parsed.latitude), longitude: Number(parsed.longitude) },
              messageSeq,
              time,
            }
          }
          break
        case 'card':
          if (parsed.userId) {
            return {
              from,
              content: String(parsed.nickname || '名片'),
              type: 'card',
              extra: { userId: String(parsed.userId), avatar: String(parsed.avatar || '') },
              messageSeq,
              time,
            }
          }
          break
        default:
          break
      }
    }
  } catch {
    // 普通文本消息，非 JSON
  }
  return { from, content: text, type: 'text', messageSeq, time }
}

/** 从原始 JSON 契约文本提取会话摘要（图片/语音等展示为占位描述） */
function digestOfText(text: string): string {
  try {
    const parsed = JSON.parse(text)
    if (parsed && typeof parsed.type === 'string') {
      switch (parsed.type) {
        case 'image':
          return '[图片]'
        case 'voice':
          return '[语音]'
        case 'location':
          return '[位置]'
        case 'card':
          return '[名片]'
        case 'match':
          return ''
        default:
          return String(parsed.content ?? '')
      }
    }
  } catch {
    // 非 JSON，按普通文本展示
  }
  return text
}

/** 全局消息监听：分发给业务回调 */
function registerMessageListener(): void {
  WKSDK.shared().chatManager.addMessageListener((message: Message) => {
    const biz = toReceivedMessage(message)
    if (biz) {
      messageHandlers.forEach((handler) => handler(biz))
    }
  })
}

/** 被踢下线监听：同账号在别处登录后重置状态 */
function registerConnectionListener(): void {
  WKSDK.shared().connectManager.addConnectStatusListener((status: ConnectStatus) => {
    if (status === ConnectStatus.ConnectKick) {
      isLoggedIn = false
    }
  })
}

/**
 * 注入 SDK 数据同步回调（会话列表 + 频道历史消息，均走 WuKongIM HTTP API）
 * wukongimjssdk 不内置 HTTP 实现，不注入则 sync()/syncMessages 直接抛错
 */
function setupProvider(apiBase: string): void {
  const sdk = WKSDK.shared()
  const uid = () => sdk.config.uid

  // 最近会话列表：POST /conversation/sync，msg_count=1 携带最后一条消息
  sdk.config.provider.syncConversationsCallback = async (): Promise<Conversation[]> => {
    const resp = await fetch(`${apiBase}/conversation/sync`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ uid: uid(), msg_count: 1 }),
    })
    if (!resp.ok) throw new Error(`conversation/sync HTTP ${resp.status}`)
    const list: Record<string, any>[] = await resp.json()
    return (Array.isArray(list) ? list : [])
      .filter((raw) => Number(raw.channel_type) === ChannelTypePerson)
      .map((raw) => {
        const channel = sdk.newChannel(String(raw.channel_id ?? ''), ChannelTypePerson)
        const conversation = sdk.conversationManager.createEmptyConversation(channel)
        conversation.unread = Number(raw.unread_count ?? raw.unread ?? 0)
        const recents: Record<string, any>[] = Array.isArray(raw.recents) ? raw.recents : []
        if (recents.length > 0) {
          const last = recents[0]
          conversation.lastMessage = rawToSdkMessage(last, channel)
          conversation.timestamp = Number(last.timestamp ?? 0)
        }
        return conversation
      })
  }

  // 频道历史消息：POST /channel/messagesync，pullMode=Up + seq=0 由服务端返回最新一页
  sdk.config.provider.syncMessagesCallback = async (
    channel: Channel,
    opts: SyncOptions
  ): Promise<Message[]> => {
    const resp = await fetch(`${apiBase}/channel/messagesync`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        login_uid: uid(),
        channel_id: channel.channelID,
        channel_type: channel.channelType,
        limit: opts.limit || 20,
        pull_mode: opts.pullMode ?? PullMode.Up,
        start_message_seq: opts.startMessageSeq || 0,
        end_message_seq: opts.endMessageSeq || 0,
      }),
    })
    if (!resp.ok) throw new Error(`channel/messagesync HTTP ${resp.status}`)
    // 响应为 {start_message_seq, end_message_seq, more, messages:[...]} 包装对象
    const data: { messages?: Record<string, any>[] } = await resp.json()
    return (Array.isArray(data?.messages) ? data.messages : []).map((raw) =>
      rawToSdkMessage(raw, channel)
    )
  }
}

/**
 * 发起连接并等待鉴权结果（不"connect 完即视为在线"）
 * @returns 超时时间内进入 Connected 返回 true；ConnectFail 或 5s 超时返回 false
 */
function connectAndWait(): Promise<boolean> {
  const sdk = WKSDK.shared()
  const connectManager = sdk.connectManager
  return new Promise((resolve) => {
    let settled = false
    const finish = (ok: boolean): void => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      connectManager.removeConnectStatusListener(listener)
      resolve(ok)
    }
    const timer = setTimeout(() => finish(connectManager.connected()), 5000)
    const listener = (status: ConnectStatus): void => {
      if (status === ConnectStatus.Connected) {
        finish(true)
      } else if (status === ConnectStatus.ConnectFail) {
        finish(false)
      }
    }
    connectManager.addConnectStatusListener(listener)
    if (connectManager.connected()) {
      finish(true)
      return
    }
    sdk.connect()
  })
}

/**
 * 登录 WuKongIM（客服账号 + token + wsUrl，凭证由后端 /admin/kefu/agent/credential 下发）
 * @returns 登录（连接鉴权）是否成功
 */
export async function loginAgent(
  username: string,
  token: string,
  wsUrl: string
): Promise<boolean> {
  if (isLoggedIn) {
    return true
  }
  try {
    const sdk = WKSDK.shared()
    sdk.config.addr = wsUrl
    sdk.config.uid = username
    sdk.config.token = token
    setupProvider(imApiBase(wsUrl))
    if (!listenerRegistered) {
      registerMessageListener()
      registerConnectionListener()
      listenerRegistered = true
    }
    const ok = await connectAndWait()
    isLoggedIn = ok
    return ok
  } catch {
    isLoggedIn = false
    return false
  }
}

/** 断开连接并重置状态 */
export function logout(): void {
  try {
    const sdk = WKSDK.shared()
    sdk.conversationManager.openConversation = undefined
    sdk.disconnect()
  } catch {
    // 静默
  }
  isLoggedIn = false
}

/** 获取登录状态 */
export function isAgentLoggedIn(): boolean {
  return isLoggedIn
}

/**
 * 获取单聊会话列表（经 HTTP /conversation/sync），映射为展示用摘要
 */
export async function getConversations(): Promise<ChatSession[]> {
  if (!isLoggedIn) {
    return []
  }
  try {
    const items = await WKSDK.shared().conversationManager.sync()
    return (items || [])
      .filter((c) => c.channel?.channelType === ChannelTypePerson)
      .map((c) => {
        const last = c.lastMessage?.content
        const lastText = last instanceof MessageText ? digestOfText(last.text || '') : ''
        return {
          conversationId: String(c.channel?.channelID || ''),
          lastMessage: lastText,
          lastTime: (c.timestamp || 0) * 1000, // WK 时间戳为秒
          unreadCount: c.unread || 0,
        }
      })
  } catch {
    return []
  }
}

/**
 * 拉取单聊历史消息（最新一页，按时间正序）
 * 经 SDK syncMessages → HTTP /channel/messagesync，与实时消息同一频道空间
 */
export async function getChatHistory(
  conversationId: string,
  pageSize = 20
): Promise<ReceivedMessage[]> {
  if (!isLoggedIn || !conversationId) {
    return []
  }
  try {
    const sdk = WKSDK.shared()
    const channel = sdk.newChannel(conversationId, ChannelTypePerson)
    const opts = new SyncOptions()
    opts.startMessageSeq = 0
    opts.endMessageSeq = 0
    opts.limit = pageSize
    opts.pullMode = PullMode.Up // 上拉模式 + seq=0：服务端返回最新一页
    const messages = await sdk.chatManager.syncMessages(channel, opts)
    return (messages || [])
      .map(toReceivedMessage)
      .filter((m): m is ReceivedMessage => m !== null)
      .sort((a, b) => a.time - b.time)
  } catch {
    return []
  }
}

/**
 * 打开发言会话：设为当前打开会话（实时消息不计未读）并清空该会话本地未读数
 */
export function markConversationRead(conversationId: string): void {
  if (!conversationId) return
  try {
    const sdk = WKSDK.shared()
    const channel = sdk.newChannel(conversationId, ChannelTypePerson)
    const conversation = sdk.conversationManager.findConversation(channel)
      || sdk.conversationManager.createEmptyConversation(channel)
    conversation.unread = 0
    sdk.conversationManager.openConversation = conversation
  } catch {
    // 静默
  }
}

/**
 * 注册消息到达回调
 * @returns 取消注册函数
 */
export function onMessageReceived(handler: MessageHandler): () => void {
  messageHandlers.push(handler)
  return () => {
    const index = messageHandlers.indexOf(handler)
    if (index >= 0) {
      messageHandlers.splice(index, 1)
    }
  }
}

/**
 * 发送单聊文本消息（含 JSON 结构化契约）
 * @returns 发送成功返回 SDK Message（含 messageSeq）；失败返回 null
 */
export async function sendTextMessage(
  to: string,
  content: string
): Promise<Message | null> {
  if (!isLoggedIn) {
    return null
  }
  try {
    const sdk = WKSDK.shared()
    const channel = sdk.newChannel(to, ChannelTypePerson)
    return await sdk.chatManager.send(sdk.newMessageText(content), channel)
  } catch {
    return null
  }
}

/**
 * 发送单聊图片消息（JSON 结构化契约 {"type":"image","url":...}，与 C 端解析一致）
 * @returns 发送成功返回 SDK Message（含 messageSeq）；失败返回 null
 */
export async function sendImageMessage(
  to: string,
  url: string
): Promise<Message | null> {
  if (!isLoggedIn) {
    return null
  }
  try {
    const sdk = WKSDK.shared()
    const channel = sdk.newChannel(to, ChannelTypePerson)
    const payload = JSON.stringify({ type: 'image', url })
    return await sdk.chatManager.send(sdk.newMessageText(payload), channel)
  } catch {
    return null
  }
}
