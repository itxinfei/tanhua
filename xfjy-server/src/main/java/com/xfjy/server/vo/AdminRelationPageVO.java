package com.xfjy.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 后台关系链分页出参。
 *
 * <p>字段沿用 admin 统一分页契约 {total, list}（与 26 号后台需求文档 §5.2 及前端 PageResult 一致）；
 * 用强类型 VO 而非 Map/List 承载，满足 AGENTS §5「禁止新增 ResponseEntity&lt;Map|List&gt;」。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminRelationPageVO {

    /** 总条数 */
    private Long total;

    /** 当前页列表 */
    private List<AdminRelationUserVO> list = Collections.emptyList();
}
