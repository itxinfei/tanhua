package com.xfjy.server.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 敏感词实体（对应 tb_sensitive_word）
 *
 * <p>level 级别：1-HIGH 直接拒绝发布，2-MID 进人工审核队列，3-LOW 替换为星号后放行</p>
 */
@Data
@TableName("tb_sensitive_word")
public class SensitiveWord extends BasePojo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 敏感词 */
    private String word;

    /** 分类：POLITICS/PORN/VIOLENCE/AD/OTHER */
    private String category;

    /** 级别：1-HIGH 2-MID 3-LOW */
    private Integer level;

    /** 状态：1-启用 0-停用 */
    private Integer status;
}