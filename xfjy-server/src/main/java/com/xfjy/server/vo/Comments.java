package com.xfjy.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评论
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Comments {

    private String id; //评论id
    private String avatar; //头像
    private String nickname; //昵称
    private String content; //评论
    private String createDate; //评论时间: 08:27
    private Integer likeCount; //点赞数
    private Integer hasLiked; //是否点赞（1是，0否）
    private String parentId; //父评论id（空=顶层评论）
    private String replyNickname; //被回复人昵称（仅回复可见）
    private Integer type; //媒体类型：1-文字 2-语音
    private String audioUrl; //语音地址（type=2 时有值）
    private Integer duration; //语音时长秒（type=2 时有值）

    /** 楼下回复（楼中楼第二层，仅楼中楼模式返回；先给最新几条预览，点「查看全部」再拉全量） */
    private java.util.List<Comments> replies;
    /** 楼下回复总数（前端据此展示「查看全部 N 条回复」；无回复为 0） */
    private Integer replyCount;

}
