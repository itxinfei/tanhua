import { get, post, upload } from "@/utils/request";
import { getToken, BASE_URL, handleUnauthorized } from "@/utils/request";
import type { PageResult, PageParam, Comment } from "@/api/circle";
import type { PicUploadResult } from "@/api/mine";

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
  /** 位置名称（城市/区域），H5 端可能为空 */
  locationName?: string;
  /** 距离（米），仅「附近」返回；为空时前端不展示坐标串，只展示位置名或不展示 */
  distance?: number;
}

/** 发布视频参数 */
export interface PublishVideoParam {
  thumbnailPath: string;
  videoPath: string;
  /** 经度（定位失败时可不传，后端允许为空） */
  longitude?: number;
  /** 纬度 */
  latitude?: number;
  /** 城市/区域名（H5 端不支持逆地理，可为空） */
  locationName?: string;
}

/**
 * 发布小视频（封面图 + 视频文件上传）
 * 对接后端: POST /small-videos（multipart/form-data）
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
      url: BASE_URL + "/small-videos",
      files: [
        { name: "videoThumbnail", uri: param.thumbnailPath },
        { name: "videoFile", uri: param.videoPath },
      ],
      header,
      formData: {
        ...(param.longitude !== undefined ? { longitude: String(param.longitude) } : {}),
        ...(param.latitude !== undefined ? { latitude: String(param.latitude) } : {}),
        ...(param.locationName ? { locationName: param.locationName } : {}),
      },
      success: (res: any) => {
        if (res.statusCode === 401) {
          handleUnauthorized();
          reject(new Error("登录已过期，请重新登录"));
          return;
        }
        if (res.statusCode >= 400) {
          uni.showToast({ title: `发布失败(${res.statusCode})`, icon: "none" });
          reject(new Error(`HTTP ${res.statusCode}`));
          return;
        }
        // 与 request 层对齐：HTTP 200 仍需校验业务 code，否则失败会被当成功
        let body: { code?: number; message?: string };
        try {
          body = typeof res.data === "string" ? JSON.parse(res.data) : res.data;
        } catch {
          uni.showToast({ title: "服务异常，请稍后再试", icon: "none" });
          reject(new Error("响应格式错误"));
          return;
        }
        if (body.code !== undefined && body.code !== 200) {
          uni.showToast({ title: body.message || "发布失败", icon: "none" });
          reject(new Error(body.message));
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
 * 对接后端: GET /small-videos
 */
export function getVideoList(params: PageParam): Promise<PageResult<Video>> {
  return get<PageResult<Video>>("/small-videos", params as any);
}

/**
 * 查询「关注」流视频（仅当前登录用户关注的人发布的视频）
 * 对接后端: GET /small-videos/focus
 */
export function getFollowVideoList(params: PageParam): Promise<PageResult<Video>> {
  return get<PageResult<Video>>("/small-videos/focus", params as any);
}

/**
 * 查询「热点」流视频（按点赞数排序）
 * 对接后端: GET /small-videos/hot
 */
export function getHotVideoList(params: PageParam): Promise<PageResult<Video>> {
  return get<PageResult<Video>>("/small-videos/hot", params as any);
}

/**
 * 查询「定位/附近」流视频：有经纬度按距离升序返回，无坐标时后端回退全站热门并带 fallback 标记
 * 对接后端: GET /small-videos/location
 */
export function getLocationVideoList(
  params: PageParam & { lat?: number; lng?: number }
): Promise<PageResult<Video>> {
  return get<PageResult<Video>>("/small-videos/location", params as any);
}

/**
 * 播放上报（后端按「用户×视频」Redis 去重，同一视频窗口期内只计一次）
 * 对接后端: POST /small-videos/{id}/play
 */
export function reportVideoPlay(id: string): Promise<void> {
  return post<void>(`/small-videos/${id}/play`);
}

/**
 * 视频点赞
 * 对接后端: POST /small-videos/{id}/like
 *
 * @returns 最新点赞数
 */
export function likeVideo(id: string): Promise<number> {
  return post<number>(`/small-videos/${id}/like`);
}

/**
 * 取消视频点赞
 * 对接后端: POST /small-videos/{id}/dislike
 *
 * @returns 最新点赞数
 */
export function dislikeVideo(id: string): Promise<number> {
  return post<number>(`/small-videos/${id}/dislike`);
}

/**
 * 查询视频评论列表
 * 对接后端: GET /small-videos/{id}/comments
 */
export function getVideoComments(
  videoId: string,
  params: PageParam
): Promise<PageResult<Comment>> {
  return get<PageResult<Comment>>(`/small-videos/${videoId}/comments`, params as any);
}

/** 发表评论参数：文字评论传 comment；语音评论传 type=2 + audioUrl + duration */
export interface PublishVideoCommentParam {
  /** 文字内容（语音评论可为空） */
  comment?: string;
  /** 媒体类型：1-文字 2-语音 */
  type?: number;
  /** 语音文件地址（先经 /upload/audio 上传 MinIO 得到） */
  audioUrl?: string;
  /** 语音时长（秒） */
  duration?: number;
}

/**
 * 发表视频评论
 * 对接后端: POST /small-videos/{id}/comments
 */
export function publishVideoComment(
  videoId: string,
  param: PublishVideoCommentParam | string
): Promise<void> {
  const body: Record<string, any> =
    typeof param === "string" ? { comment: param } : { ...param };
  return post<void>(`/small-videos/${videoId}/comments`, body);
}

/**
 * 上传语音评论音频（先传 MinIO 拿 URL，再以 audioUrl 提交评论）
 * 对接后端: POST /upload/audio（multipart/form-data，字段名 file）
 *
 * @param filePath 录音得到的本地临时文件路径
 * @returns 音频访问 URL
 */
export function uploadAudio(filePath: string): Promise<string> {
  return upload<PicUploadResult>("/upload/audio", [filePath], "file").then(
    (res) => {
      if (res.status !== "done" || !res.name) {
        throw new Error("音频上传失败");
      }
      return res.name;
    }
  );
}

/**
 * 视频评论点赞
 * 对接后端: POST /small-videos/comments/{id}/like
 *
 * @returns 最新点赞数
 */
export function likeVideoComment(id: string): Promise<number> {
  return post<number>(`/small-videos/comments/${id}/like`);
}

/**
 * 取消视频评论点赞
 * 对接后端: POST /small-videos/comments/{id}/dislike
 *
 * @returns 最新点赞数
 */
export function dislikeVideoComment(id: string): Promise<number> {
  return post<number>(`/small-videos/comments/${id}/dislike`);
}

/**
 * 关注视频作者
 * 对接后端: POST /small-videos/{id}/userFocus
 */
export function focusVideoUser(userId: string): Promise<void> {
  return post<void>(`/small-videos/${userId}/userFocus`);
}

/**
 * 取消关注视频作者
 * 对接后端: POST /small-videos/{id}/userUnFocus
 */
export function unfocusVideoUser(userId: string): Promise<void> {
  return post<void>(`/small-videos/${userId}/userUnFocus`);
}