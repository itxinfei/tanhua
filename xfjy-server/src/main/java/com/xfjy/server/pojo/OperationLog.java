package com.xfjy.server.pojo;

import lombok.Data;

import java.util.Date;

/**
 * 后台操作日志实体
 */
@Data
public class OperationLog implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long adminId;       // 操作管理员ID
    private String adminUsername; // 操作管理员账号
    private String module;      // 模块
    private String action;      // 操作类型
    private String targetId;    // 操作对象ID
    private String detail;      // 操作详情摘要
    private String ip;          // 操作IP
    private Date created;       // 操作时间
}