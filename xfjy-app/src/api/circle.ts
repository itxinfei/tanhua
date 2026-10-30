import { get, post, upload } from "@/utils/request";

/** 分页结果（后端 PageResult 结构） */
export interface PageResult<T = any> {
  page: number;
  pagesize: number;
  pages: number;
  counts: number;
  items: T[];
}

/** 分页查询参数 */
export interface PageParam {
  page: number;
  pagesize: number;
}

/** 圈子动态（与后端 com.xfjy.server.vo.Movements 精确对齐） */
export interface Movement {
  id: string;
  userId: string;
  nickname: string;
  avatar: string;
  gender: string;
  age: number;
  tags?: string[];
  textContent: string;
  /** 图片URL数组（后端字段名 imageContent） */
  imageContent: string[];
  /** 距离（后端为字符串，如"1.2km"） */
  distance?: string;
  /** 发布位置（后端 Movements VO 字段） */
  location?: string;
  createDate: string;
  /** 点赞数 */
  likeCount: number;
  commentCount: number;
  /** 喜欢数 */
  loveCount: number;
  hasLiked: number;
  hasLoved: number;
}

/** 访客记录 */
export interface Visitor {
  id: string;
  visitorUserId: string;
  nickname: string;
  avatar: string;
  gender: string;
  age: number;
  city: string;
  date: string;
}

/** 评论（与后端 com.xfjy.server.vo.Comments 精确对齐） */
export interface Comment {
  id: string;
  avatar: string;
  nickname: string;
  /** 评论内容（后端字段名 content） */
  content: string;
  createDate: string;
  likeCount: number;
  hasLiked: number;
  /** 父评论id（空=顶层评论） */
  parentId?: string;
  /** 被回复人昵称（仅回复可见） */
  replyNickname?: string;
}

/** 发布动态参数 */
export interface PublishParam {
  textContent: string;
  location: string;
  longitude: string;
  latitude: string;
  imagePaths: string[];
}

/**
 * 发布动态（含图片上传）
 * 对接后端: POST /movements（multipart/form-data）
 *
 * @param param 文字内容、位置、经纬度、本地图片路径列表
 */
export function publishMovement(param: PublishParam): Promise<void> {
  return upload<void>(
    "/movements",
    param.imagePaths,
    "imageContent",
    {
      textContent: param.textContent,
      location: param.location,
      longitude: param.longitude,
      latitude: param.latitude,
    }
  );
}

/**
 * 查询好友动态列表（时间线）
 * 对接后端: GET /movements
 */
export function getFriendMovements(params: PageParam): Promise<PageResult<Movement>> {
  return get<PageResult<Movement>>("/movements", params as any);
}

/**
 * 查询推荐动态列表（评分排序）
 * 对接后端: GET /movements/recommend
 */
export function getRecommendMovements(params: PageParam): Promise<PageResult<Movement>> {
  return get<PageResult<Movement>>("/movements/recommend", params as any);
}

/**
 * 查询动态详情
 * 对接后端: GET /movements/{id}
 */
export function getMovementById(id: string): Promise<Movement> {
  return get<Movement>(`/movements/${id}`);
}

/**
 * 查询个人动态列表（个人主页）
 * 对接后端: GET /movements/all
 */
export function getUserMovements(
  userId: string,
  params: PageParam
): Promise<PageResult<Movement>> {
  return get<PageResult<Movement>>("/movements/all", {
    userId,
    ...params,
  });
}

/**
 * 查询谁看过我
 * 对接后端: GET /movements/visitors
 */
export function getVisitors(): Promise<Visitor[]> {
  return get<Visitor[]>("/movements/visitors");
}

/**
 * 动态点赞
 * 对接后端: GET /movements/{id}/like
 *
 * @returns 最新点赞数
 */
export function likeMovement(id: string): Promise<number> {
  return get<number>(`/movements/${id}/like`);
}

/**
 * 取消动态点赞
 * 对接后端: GET /movements/{id}/dislike
 *
 * @returns 最新点赞数
 */
export function dislikeMovement(id: string): Promise<number> {
  return get<number>(`/movements/${id}/dislike`);
}

/**
 * 动态喜欢
 * 对接后端: GET /movements/{id}/love
 *
 * @returns 最新喜欢数
 */
export function loveMovement(id: string): Promise<number> {
  return get<number>(`/movements/${id}/love`);
}

/**
 * 取消动态喜欢
 * 对接后端: GET /movements/{id}/unlove
 *
 * @returns 最新喜欢数
 */
export function unloveMovement(id: string): Promise<number> {
  return get<number>(`/movements/${id}/unlove`);
}

/**
 * 查询评论列表
 * 对接后端: GET /comments
 */
export function getComments(
  movementId: string,
  params: PageParam
): Promise<PageResult<Comment>> {
  return get<PageResult<Comment>>("/comments", {
    movementId,
    ...params,
  });
}

/**
 * 发表评论
 * 对接后端: POST /comments
 */
export function publishComment(movementId: string, comment: string): Promise<void> {
  return post<void>("/comments", { movementId, comment });
}

/**
 * 评论点赞
 * 对接后端: GET /comments/{id}/like
 *
 * @returns 最新点赞数
 */
export function likeComment(id: string): Promise<number> {
  return get<number>(`/comments/${id}/like`);
}

/**
 * 取消评论点赞
 * 对接后端: GET /comments/{id}/dislike
 *
 * @returns 最新点赞数
 */
export function dislikeComment(id: string): Promise<number> {
  return get<number>(`/comments/${id}/dislike`);
}