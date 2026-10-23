package com.xfjy.dubbo.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户信息条件查询参数（替代原 QueryWrapper 透传，支持 HTTP 传输）
 * 所有条件均为可选，为空则不加入 SQL 条件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoQueryParam implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户id列表（in 查询，为空则不加 in 条件）
     */
    private List<Long> userIds;

    /**
     * 昵称模糊查询（like，为空则不加 like 条件）
     */
    private String nickName;

    /**
     * 性别值（eq，1-男 2-女 3-未知，为空则不加 eq 条件）
     */
    private Integer sex;
}