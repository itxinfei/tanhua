/**
 * IM 业务事件总线（零 SDK 依赖）
 *
 * 背景：wukongim.ts 静态依赖 wukongimjssdk（构建产物 utils-wukongim chunk 约 191K），
 * 而首屏链 App.vue → global-tabbar → store/unread 曾静态 import 它，导致 IM SDK 进入首屏 chunk。
 * 本总线把「事件订阅」与「SDK 生命周期」解耦：页面/Store 只依赖本模块，
 * wukongim 建立连接后通过 emit* 转发 SDK 事件，使 wukongim.ts 可被动态 import 移出首屏。
 *
 * 约定：业务侧只用 on* 注册；emit* 仅供 wukongim.ts 内部转发，不在页面调用。
 */

/** 收到的业务消息（已从 JSON 契约解析） */
export interface ReceivedMessage {
  from: string;
  content: string;
  type: "text" | "image" | "voice" | "location" | "card";
  extra?: Record<string, unknown>;
  time?: number;
  /** 消息序号（WuKongIM messageSeq）：续拉更旧历史用（M-K4） */
  seq?: number;
  /** 客户端消息号（WuKongIM clientMsgNo）：乐观气泡与回包/历史合并的唯一键 */
  clientMsgNo?: string;
}

/** 匹配事件（互喜时后端经 IM 透传触发） */
export interface MatchEvent {
  from: string;
  nickname: string;
  avatar: string;
}

/** 系统通知（后端 PushService 经 IM 旁路透传，覆盖 H5 及在线端的点赞/评论提醒） */
export interface SystemNotice {
  /** 通知类别：like | comment */
  category: "like" | "comment";
  title: string;
  content: string;
  time?: number;
}

type MessageHandler = (msg: ReceivedMessage) => void;
type MatchHandler = (event: MatchEvent) => void;
type SystemHandler = (notice: SystemNotice) => void;

const messageHandlers: MessageHandler[] = [];
const matchHandlers: MatchHandler[] = [];
const systemHandlers: SystemHandler[] = [];

/** 注册消息到达回调，返回取消注册函数 */
export function onMessageReceived(handler: MessageHandler): () => void {
  messageHandlers.push(handler);
  return () => {
    const i = messageHandlers.indexOf(handler);
    if (i >= 0) messageHandlers.splice(i, 1);
  };
}

/** 注册匹配事件回调，返回取消注册函数 */
export function onMatchReceived(handler: MatchHandler): () => void {
  matchHandlers.push(handler);
  return () => {
    const i = matchHandlers.indexOf(handler);
    if (i >= 0) matchHandlers.splice(i, 1);
  };
}

/** 注册系统通知回调（点赞/评论 IM 旁路提醒），返回取消注册函数 */
export function onSystemNoticeReceived(handler: SystemHandler): () => void {
  systemHandlers.push(handler);
  return () => {
    const i = systemHandlers.indexOf(handler);
    if (i >= 0) systemHandlers.splice(i, 1);
  };
}

/** 供 wukongim.ts 转发 SDK 消息事件 */
export function emitMessage(msg: ReceivedMessage): void {
  messageHandlers.forEach((h) => h(msg));
}

/** 供 wukongim.ts 转发匹配事件 */
export function emitMatch(event: MatchEvent): void {
  matchHandlers.forEach((h) => h(event));
}

/** 供 wukongim.ts 转发系统通知 */
export function emitSystemNotice(notice: SystemNotice): void {
  systemHandlers.forEach((h) => h(notice));
}
