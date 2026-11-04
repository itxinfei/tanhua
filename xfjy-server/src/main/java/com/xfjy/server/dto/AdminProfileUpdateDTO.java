package com.xfjy.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台代改用户资料入参（白名单字段）。
 *
 * <p>字段命名对齐 C 端 {@code UserInfoVo}（gender/education/profession），
 * 由 BFF 映射为 {@code UserInfo} 后调 user-service 更新。所有字段均可选：
 * 字段为 {@code null} 表示本次不修改；空串 "" 表示清空（性别、婚姻除外）。</p>
 */
@Data
public class AdminProfileUpdateDTO {

    /** 昵称：传了就必须 2-20 字符；null 表示不改 */
    @Size(min = 2, max = 20, message = "昵称长度需2-20个字符")
    private String nickname;

    /** 头像 MinIO 访问 URL */
    @Size(max = 500, message = "头像地址过长")
    private String avatar;

    /** 个性签名，最多 50 字符；空串表示清空 */
    @Size(max = 50, message = "签名不能超过50个字符")
    private String signature;

    /** 性别：man / woman（不开放 unknown，避免后台把用户资料改成未知性别） */
    @Pattern(regexp = "man|woman", message = "性别取值非法")
    private String gender;

    /** 城市，最多 20 字符；空串表示清空 */
    @Size(max = 20, message = "城市不能超过20个字符")
    private String city;

    /** 身高（cm）100-250，允许空串清空 */
    @Pattern(regexp = "|1\\d{2}|2[0-4]\\d|250", message = "身高取值非法（100-250cm）")
    private String height;

    /** 学历枚举，与 C 端 EDUCATION_OPTIONS 一致；空串表示清空 */
    @Pattern(regexp = "|高中|大专|本科|硕士|博士", message = "学历取值非法")
    private String education;

    /** 行业，最多 20 字符；空串表示清空 */
    @Size(max = 20, message = "行业不能超过20个字符")
    private String profession;

    /** 照片墙：JSON 数组字符串（元素为图片 URL），最多 6 张；空串表示清空。具体解析与张数校验在 Service 层 */
    private String photoAlbum;

    /** 收入（自由文本，如 15000-20000），最多 20 字符；空串表示清空 */
    @Size(max = 20, message = "收入不能超过20个字符")
    private String income;

    /**
     * 婚姻状态：0 未婚 / 1 已婚。
     * 口径对齐 C 端 {@code UsersService}（==1 存"已婚"，否则"未婚"）；
     * 26 号文档写的 1 离异 / 2 丧偶 在 C 端无法正确展示，属待裁决冲突，暂不开放。
     */
    @Min(value = 0, message = "婚姻状态取值非法")
    @Max(value = 1, message = "婚姻状态取值非法")
    private Integer marriage;
}
