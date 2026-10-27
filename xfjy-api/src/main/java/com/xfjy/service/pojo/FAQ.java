package com.xfjy.service.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 常见问题 FAQ（客服系统阶段1）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_faq")
public class FAQ implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 分类（账号安全/会员权益/匹配交友/聊天互动/举报投诉） */
    private String category;
    /** 问题（唯一） */
    private String question;
    /** 答案 */
    private String answer;
    /** 排序权重，越小越靠前 */
    private Integer sort;
    /** 状态：1-启用，0-停用 */
    private Integer status;
    private Date created;
    private Date updated;
}