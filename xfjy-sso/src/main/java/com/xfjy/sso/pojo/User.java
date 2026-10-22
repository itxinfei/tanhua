package com.xfjy.sso.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户表 tb_user（SSO 独占存储）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String mobile; //手机号

    @JsonIgnore
    private String password; //密码，JSON 序列化时忽略（与 server 侧契约一致）

    private String status; //状态：NORMAL-正常，FROZEN-已封禁

    private String freezeReason; //封禁原因

    private Date created;

    private Date updated;
}
