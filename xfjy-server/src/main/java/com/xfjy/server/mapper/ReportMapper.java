package com.xfjy.server.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xfjy.server.pojo.Report;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface ReportMapper extends BaseMapper<Report> {

    /**
     * 按目标ID批量统计举报数（target_type=POST，动态举报）
     * 举报动态时 targetId 存的是 publish.pid（Long 类型，publishId 为 ObjectId 存不下）
     *
     * @param targetIds 目标ID列表（动态的 pid）
     * @return target_id -> 举报数
     */
    default Map<Long, Long> countReportsByTargetIds(List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<Report> wrapper = new QueryWrapper<>();
        wrapper.eq("target_type", "POST")
                .in("target_id", targetIds)
                .select("target_id", "count(*) as cnt")
                .groupBy("target_id");
        List<Map<String, Object>> rows = selectMaps(wrapper);
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object targetId = row.get("target_id");
            Object cnt = row.get("cnt");
            if (targetId != null && cnt != null) {
                result.put(((Number) targetId).longValue(), ((Number) cnt).longValue());
            }
        }
        return result;
    }
}