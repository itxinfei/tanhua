package com.xfjy.service.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热搜词条目
 *
 * <p>rank 为名次（从 1 开始，前端对前三名做强调）；heat 为热度值，
 * 当前无统计来源时为 null（前端不展示热度数字，只用名次）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotSearchWord implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 名次，1 开始 */
    private Integer rank;

    /** 搜索词 */
    private String word;

    /** 热度值（可空） */
    private Long heat;
}
