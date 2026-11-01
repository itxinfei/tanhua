package com.xfjy.user.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 滑动日志实体（tb_swipe_log）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_swipe_log")
public class SwipeLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起滑动用户 ID */
    private Long userId;

    /** 被滑动用户 ID */
    private Long targetId;

    /** 动作：like|superlike|dislike */
    private String action;

    /** 是否互喜 */
    private Integer matched;

    /** 滑动时间 */
    private LocalDateTime created;
}
