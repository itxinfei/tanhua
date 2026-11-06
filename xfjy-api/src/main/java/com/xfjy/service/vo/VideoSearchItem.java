package com.xfjy.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频搜索结果条目（跨服务传输对象）
 *
 * <p>字段为 BFF 与前端搜索页的最小共识：标题、封面、作者、点赞数。
 * 播放量（playCount）已落库计数，历史数据无该字段时按 0 返回。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoSearchItem implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 视频 ObjectId 十六进制字符串 */
    private String id;

    /** 自增视频号（历史数据可能为空） */
    private Long vid;

    /** 标题/描述（video.text，视频集合没有独立 title 字段） */
    private String title;

    /** 封面地址 */
    private String cover;

    /** 视频播放地址（MinIO URL） */
    private String videoUrl;

    /** 作者用户ID */
    private Long authorId;

    /** 作者昵称（由 BFF 补齐，域服务返回时为空） */
    private String authorName;

    /** 点赞数（Redis 点赞计数快照） */
    private Integer likeCount;

    /** 播放量（Mongo 落库计数，历史数据无该字段时按 0 返回） */
    private Long playCount;
}
