package com.xfjy.server.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 协议管理实体（对应 tb_agreement）
 * type: USER(用户协议) / PRIVACY(隐私政策)
 * status: DRAFT(草稿) / PUBLISHED(已发布) / OFFLINE(已下线)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_agreement")
public class Agreement extends BasePojo {

    private Long id;
    private String type;
    private String title;
    private String content;
    private Integer version;
    private String status;
    private String publisher;
    private Date publishedAt;
}
