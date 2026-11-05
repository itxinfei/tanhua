package com.xfjy.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频搜索结果 VO（前端搜索页双列瀑布流使用）
 *
 * <p>与 {@code com.xfjy.service.vo.VideoSearchItem} 同名同义，仅保留前端需要的字段。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoSearchVo {

    /** 视频ID（ObjectId 十六进制） */
    private String id;

    /** 自增视频号 */
    private Long vid;

    /** 标题/描述（video.text） */
    private String title;

    /** 封面地址 */
    private String cover;

    /** 作者用户ID */
    private Long authorId;

    /** 作者昵称（用户服务补齐） */
    private String authorName;

    /** 点赞数 */
    private Integer likeCount;

    /** 播放量（暂无采集字段，返回 null） */
    private Long playCount;
}
