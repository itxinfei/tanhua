/**
 * 头像口径唯一真源。
 *
 * App 裁剪页、编辑资料页与后续管理端共用同一组数值；
 * 调整头像规格只改这里，禁止在页面里再写一套。
 */

/** 裁剪取景框比例（宽:高），后台/服务端按同口径校验 */
export const AVATAR_CROP_RATIO = 1;

/** 导出边长（px）：1:1、400×400，兼顾清晰度与上传体积 */
export const AVATAR_OUTPUT_SIZE = 400;

/** 源图最小边长（px）：低于此值放大后会明显糊，选图阶段直接拦截 */
export const AVATAR_MIN_SOURCE_SIZE = 200;

/** 导出图片格式：jpg 兼容性最好，webp 在部分小程序 canvas 不可用 */
export const AVATAR_CROP_FILE_TYPE = "jpg";

/** 导出质量（仅 jpg 生效） */
export const AVATAR_CROP_QUALITY = 0.9;

/** 缩放上限：避免过度放大导致像素化 */
export const AVATAR_MAX_ZOOM = 4;

/** 上传体积上限 2MB */
export const AVATAR_MAX_FILE_SIZE = 2 * 1024 * 1024;

/** 允许的 MIME 类型（部分平台 chooseImage 不返回 type，按扩展名兜底） */
export const AVATAR_ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];

/** 允许的扩展名 */
export const AVATAR_ACCEPTED_EXTS = ["jpg", "jpeg", "png", "webp"];
