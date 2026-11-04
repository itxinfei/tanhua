package com.xfjy.server.vo;

import lombok.Data;

import java.util.Date;

/**
 * 用户资料编辑历史出参（来源 tb_operation_log，action=EDIT_PROFILE）。
 *
 * <p>受 AGENTS §3「BFF 禁止新增持久化表」约束，不为字段级日志单独建表，
 * 一次保存记一条，{@code detail} 承载本次所有字段的「旧值→新值」摘要。</p>
 */
@Data
public class ProfileLogVO {

    /** 操作日志 ID */
    private Long id;

    /** 操作管理员 ID */
    private Long operatorId;

    /** 操作管理员账号 */
    private String operatorName;

    /** 变更摘要，如「昵称[张三→李四]; 城市[北京→上海]」 */
    private String detail;

    /** 操作 IP */
    private String ip;

    /** 操作时间（ISO 字符串，前端 formatDateTime 解析） */
    private Date createdAt;
}
