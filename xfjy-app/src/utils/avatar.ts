/** 默认头像（透明 logo，项目 static 目录） */
export const DEFAULT_AVATAR = "/static/logo/logo-icon-transparent.png";

/**
 * 解析头像 URL：空值回退默认 logo，非空原样返回
 */
export function resolveAvatar(url: string | undefined | null): string {
  return url && url.trim() !== "" ? url : DEFAULT_AVATAR;
}
