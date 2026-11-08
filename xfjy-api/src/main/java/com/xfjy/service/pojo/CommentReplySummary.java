package com.xfjy.service.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 顶层评论的回复汇总（楼中楼列表装配用）
 *
 * <p>一次查询同时给出「回复总数」与「最新 N 条回复预览」，避免 BFF 对每条顶层评论
 * 各发一次 count + find（N+1 查询）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentReplySummary implements Serializable {

    private static final long serialVersionUID = 2026110801L;

    /** 该顶层评论下未删除回复总数 */
    private Long total;

    /** 回复预览（按 created 升序，即时间正序；最多 previewSize 条，取的是最新的一段） */
    private List<Comment> replies;
}
