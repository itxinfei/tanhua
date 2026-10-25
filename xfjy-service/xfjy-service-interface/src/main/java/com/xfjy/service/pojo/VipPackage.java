package com.xfjy.service.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * VIP 套餐
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_vip_package")
public class VipPackage implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 套餐名称（月卡/季卡/年卡） */
    private String name;
    /** VIP 等级（1-月卡会员，2-季卡会员，3-年卡会员） */
    private Integer level;
    /** 售价，单位：分 */
    private Long price;
    /** 原价，单位：分 */
    private Long originalPrice;
    /** 有效天数（30/90/365） */
    private Integer durationDays;
    /** 套餐描述 */
    private String description;
    /** 状态：1-上架，0-下架 */
    private Integer status;
    /** 排序权重，越小越靠前 */
    private Integer sort;
    private Date created;
    private Date updated;
}