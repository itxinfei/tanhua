package com.xfjy.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xfjy.user.pojo.SwipeLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
     * 统计今日已滑动次数。
     *
     * <p>口径：like / dislike / superlike 三种动作均计入，与 BFF checkSwipeLimit 一致
     * （2026-11-04 裁定：滑走并换成下一个用户即消耗一次，与滑动方向无关）。</p>
     */
    @Select("SELECT COUNT(*) FROM tb_swipe_log " +
            "WHERE user_id = #{userId} AND DATE(created) = CURDATE()")
    Long countTodaySwipes(@Param("userId") Long userId);

    /**
     * 后台看板：今日匹配漏斗（无行时 SUM 返回 NULL，由调用方兜底为 0）
     *
     * swipes 总滑动数（含 dislike）；likes 右滑数（like+superlike）；
     * superlikes 超级喜欢数；matches 互喜匹配数；
     * likeUsers 右滑去重用户数；matchedUsers 发生匹配的去重用户数
     */
    @Select("SELECT COUNT(*) AS swipes, " +
            "COALESCE(SUM(CASE WHEN action IN ('like','superlike') THEN 1 ELSE 0 END), 0) AS likes, " +
            "COALESCE(SUM(CASE WHEN action = 'superlike' THEN 1 ELSE 0 END), 0) AS superlikes, " +
            "COALESCE(SUM(CASE WHEN matched = 1 THEN 1 ELSE 0 END), 0) AS matches, " +
            "COUNT(DISTINCT CASE WHEN action IN ('like','superlike') THEN user_id END) AS likeUsers, " +
            "COUNT(DISTINCT CASE WHEN matched = 1 THEN user_id END) AS matchedUsers " +
            "FROM tb_swipe_log WHERE created >= CURDATE()")
    Map<String, Object> selectTodayFunnel();

    /**
     * 后台看板：起始时间以来每日匹配漏斗，按天分组（缺日由调用方补零）
     */
    @Select("SELECT DATE(created) AS d, COUNT(*) AS swipes, " +
            "COALESCE(SUM(CASE WHEN action IN ('like','superlike') THEN 1 ELSE 0 END), 0) AS likes, " +
            "COALESCE(SUM(CASE WHEN action = 'superlike' THEN 1 ELSE 0 END), 0) AS superlikes, " +
            "COALESCE(SUM(CASE WHEN matched = 1 THEN 1 ELSE 0 END), 0) AS matches " +
            "FROM tb_swipe_log WHERE created >= #{start} " +
            "GROUP BY DATE(created)")
    List<Map<String, Object>> selectDailyStats(@Param("start") LocalDateTime start);
}
