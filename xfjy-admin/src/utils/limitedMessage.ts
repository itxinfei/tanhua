import { ElMessage, type MessageHandler } from 'element-plus'

/**
 * 限流错误提示：修复响应拦截器在并发失败时（典型场景：token 过期瞬间
 * 十几个请求同时 401）连弹一屏 toast 的问题。
 * 规则：
 * 1. 相同文案去重——已存在同文案提示时忽略新调用，关闭后才可再次弹出；
 * 2. 全局同时最多 MAX_VISIBLE 条，超出时关闭最早一条。
 * 仅收敛网络层错误提示；业务页用户主动操作的 ElMessage 不走这里。
 */
const MAX_VISIBLE = 3

const dedup = new Map<string, MessageHandler>()
const active: MessageHandler[] = []

export const limitedMessage = {
  error(message: string) {
    if (dedup.has(message)) return
    const handler = ElMessage({
      type: 'error',
      message,
      onClose: () => {
        dedup.delete(message)
        const idx = active.indexOf(handler)
        if (idx > -1) active.splice(idx, 1)
      },
    })
    dedup.set(message, handler)
    active.push(handler)
    while (active.length > MAX_VISIBLE) {
      active.shift()?.close()
    }
  },
}
