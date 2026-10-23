package com.xfjy.service.pojo;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.util.Date;

/**
 * 通用基础实体（created/updated 由 MetaObjectHandler 自动填充）
 */
@Data
public abstract class BasePojo implements java.io.Serializable {

    @TableField(fill = FieldFill.INSERT)
    private Date created;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updated;
}