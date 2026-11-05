package com.xfjy.server.vo;

import lombok.Data;

/**
 * 后台关系链列表项出参（关注/粉丝/匹配通用）。
 *
 * <p>关系记录（MongoDB user_like）只含双方 userId 与时间，展示资料由 BFF 用对方 userId
 * 批量回查 tb_user_info 补全；gender 沿用 admin 既有出参词表（男/女/未知，取 SexEnum.toString()），
 * 与用户列表/详情同源。</p>
 */
@Data
public class AdminRelationUserVO {

    /** 对方用户 ID */
    private Long userId;

    /** 昵称（资料缺失时回退「用户{id}」） */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 性别：男/女/未知 */
    private String gender;

    /** 年龄 */
    private Integer age;

    /** 城市 */
    private String city;

    /** 关系建立时间，格式 yyyy-MM-dd HH:mm:ss（Asia/Shanghai） */
    private String createdAt;
}
