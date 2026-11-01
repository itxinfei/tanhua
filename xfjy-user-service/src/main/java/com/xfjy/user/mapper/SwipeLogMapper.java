package com.xfjy.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xfjy.user.pojo.SwipeLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 滑动日志 Mapper（tb_swipe_log 由 user-service 独占）
 *
 * <p>recordSwipe 使用 INSERT IGNORE 防御极小概率的重复写入（前端重试/幂等保护）；countTodaySwipes 使用 DATE(created)=CURDATE() 统计当日次数。</p>
 */
@Mapper
public interface SwipeLogMapper extends BaseMapper<SwipeLog> {

    /**
     * 记录一次滑动动作
     *
     * @return 写入行数（0=重复被幂等跳过，1=写入成功）
     */
    @Insert("INSERT IGNORE INTO tb_swipe_log (user_id, target_id, action, matched, created) " +
            "VALUES (#{userId}, #{targetId}, #{action}, #{matched}, NOW())")
    int recordSwipe(@Param("userId") Long userId,
                    @Param("targetId") Long targetId,
                    @Param("action") String action,
                    @Param("matched") Integer matched);

    /**
     * 统计今日已滑动次数（所有 action 类型均计入总次数）
     */
    @Select("SELECT COUNT(*) FROM tb_swipe_log " +
            "WHERE user_id = #{userId} AND DATE(created) = CURDATE()")
    Long countTodaySwipes(@Param("userId") Long userId);
}
