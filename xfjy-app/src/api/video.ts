import { get, post } from "@/utils/request";
import { getToken, BASE_URL } from "@/utils/request";
import type { PageResult, PageParam, Comment } from "@/api/circle";

/**
 * 小视频
 * 与后端 com.xfjy.server.vo.VideoVo 精确对齐
 */
export interface Video {
  /** 视频ID */
  id: string;
  /** 作者用户ID */
  userId: string;
  /** 作者昵称 */
  nickname: string;
  /** 作者头像 */
  avatar: string;
  /** 签名/描述 */
  signature: string;
  /** 封面图URL */
  cover: string;
  /** 视频URL */
  videoUrl: string;
  /** 是否已关注作者（1是 0否） */
  hasFocus: number;
  /** 是否已点赞（1是 0否） */
  hasLiked: number;
  /** 点赞数 */
  likeCount: number;
  /** 评论数 */
  commentCount: number;
}

/** 发布视频参数 */
export interface PublishVideoParam {
  thumbnailPath: string;
  videoPath: string;
}

/**
 * 发布小视频（封面图 + 视频文件上传）
 * 对接后端: POST /smallVideos（multipart/form-data）
 * 后端接收两个字段: videoThumbnail（封面图）、videoFile（视频文件）
 *
 * @param param 封面图本地路径、视频本地路径
 */
export function publishVideo(param: PublishVideoParam): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    const token = getToken();
    const header: Record<string, string> = {};
    if (token) {
      header["Authorization"] = `Bearer ${token}`;
    }

    uni.uploadFile({
      url: BASE_URL + "/smallVideos",
      files: [
        { name: "videoThumbnail", uri: param.thumbnailPath },
        { name: "videoFile", uri: param.videoPath },
      ],
      header,
      success: (res: any) => {
        if (res.statusCode >= 400) {
          uni.showToast({ title: `发布失败(${res.statusCode})`, icon: "none" });
          reject(new Error(`HTTP ${res.statusCode}`));
          return;
        }
        resolve();
      },
      fail: (err: any) => {
        uni.showToast({ title: "网络异常，请检查网络", icon: "none" });
        reject(err);
      },
    });
  });
}

/**
 * 查询小视频列表（推荐排序）
 * 对接后端: GET /smallVideos
 */
export function getVideoList(params: PageParam): Promise<PageResult<Video>> {
  return get<PageResult<Video>>("/smallVideos", params as any);
}

/**
 * 视频点赞
 * 对接后端: POST /smallVideos/{id}/like
 *
 * @returns 最新点赞数
 */
export function likeVideo(id: string): Promise<number> {
  return post<number>(`/smallVideos/${id}/like`);
}

/**
 * 取消视频点赞
 * 对接后端: POST /smallVideos/{id}/dislike
 *
 * @returns 最新点赞数
 */
export function dislikeVideo(id: string): Promise<number> {
  return post<number>(`/smallVideos/${id}/dislike`);
}

/**
 * 查询视频评论列表
 * 对接后端: GET /smallVideos/{id}/comments
 */
export function getVideoComments(
  videoId: string,
  params: PageParam
): Promise<PageResult<Comment>> {
  return get<PageResult<Comment>>(`/smallVideos/${videoId}/comments`, params as any);
}

/**
 * 发表视频评论
 * 对接后端: POST /smallVideos/{id}/comments
 */
export function publishVideoComment(videoId: string, comment: string): Promise<void> {
  return post<void>(`/smallVideos/${videoId}/comments`, { comment });
}

/**
 * 视频评论点赞
 * 对接后端: POST /smallVideos/comments/{id}/like
 *
 * @returns 最新点赞数
 */
export function likeVideoComment(id: string): Promise<number> {
  return post<number>(`/smallVideos/comments/${id}/like`);
}

/**
 * 取消视频评论点赞
 * 对接后端: POST /smallVideos/comments/{id}/dislike
 *
 * @returns 最新点赞数
 */
export function dislikeVideoComment(id: string): Promise<number> {
  return post<number>(`/smallVideos/comments/${id}/dislike`);
}

/**
 * 关注视频作者
 * 对接后端: POST /smallVideos/{id}/userFocus
 */
export function focusVideoUser(userId: string): Promise<void> {
  return post<void>(`/smallVideos/${userId}/userFocus`);
}

/**
 * 取消关注视频作者
 * 对接后端: POST /smallVideos/{id}/userUnFocus
 */
export function unfocusVideoUser(userId: string): Promise<void> {
  return post<void>(`/smallVideos/${userId}/userUnFocus`);
}