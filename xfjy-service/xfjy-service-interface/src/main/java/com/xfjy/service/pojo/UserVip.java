package com.xfjy.service.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户 VIP 记录
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_user_vip")
public class UserVip implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 用户 id */
    private Long userId;
    /** 当前 VIP 等级（1/2/3，0 或记录不存在表示非会员） */
    private Integer level;
    /** VIP 到期时间 */
    private Date expireTime;
    /** 开通来源：PAY-支付开通，GIFT-运营赠送 */
    private String source;
    private Date created;
    private Date updated;
}