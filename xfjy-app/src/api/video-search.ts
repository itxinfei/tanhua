import { get } from "@/utils/request";
import type { PageResult } from "@/api/circle";

/** 视频搜索结果条目（与后端 com.xfjy.server.vo.VideoSearchVo 对齐） */
export interface VideoSearchItem {
  /** 视频ID（ObjectId 十六进制） */
  id: string;
  /** 自增视频号 */
  vid: number | null;
  /** 标题/描述 */
  title: string;
  /** 封面 */
  cover: string;
  /** 视频播放地址（MinIO URL，TASK-20261106-04 全链路透传） */
  videoUrl: string;
  /** 作者用户ID */
  authorId: number | null;
  /** 作者昵称（用户服务补齐，补不到时为空） */
  authorName: string | null;
  /** 点赞数 */
  likeCount: number;
  /** 播放量 */
  playCount: number | null;
}

/** 热搜词（带名次） */
export interface HotSearchWord {
  rank: number;
  word: string;
  heat: number | null;
}

/**
 * 搜索视频
 * GET /small-videos/search
 */
export function searchVideos(params: {
  keyword: string;
  page: number;
  pageSize: number;
}) {
  return get<PageResult<VideoSearchItem>>("/small-videos/search", params);
}

/**
 * 热搜榜
 * GET /small-videos/search/hot
 */
export function getHotSearchWords() {
  return get<HotSearchWord[]>("/small-videos/search/hot");
}
