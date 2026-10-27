package com.xfjy.service.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 意见反馈（客服系统阶段1）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_feedback")
public class Feedback implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 提交用户ID */
    private Long userId;
    /** 反馈内容 */
    private String content;
    /** 联系方式（可选） */
    private String contact;
    /** 客服回复 */
    private String reply;
    /** 状态：0-待处理，1-已回复，2-已关闭 */
    private Integer status;
    private Date created;
    private Date updated;
}