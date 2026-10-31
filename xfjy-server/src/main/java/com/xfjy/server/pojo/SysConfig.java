package com.xfjy.server.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通用系统配置（tb_sys_config）
 * 运行时可调的系统级配置，管理入口：后台「系统配置」页
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_sys_config")
public class SysConfig extends BasePojo {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键，如 im.provider */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置说明 */
    private String description;
}