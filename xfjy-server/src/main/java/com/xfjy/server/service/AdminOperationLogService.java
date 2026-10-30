package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.server.mapper.OperationLogMapper;
import com.xfjy.server.pojo.OperationLog;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台操作日志查询服务
 */
@Service
public class AdminOperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    /**
     * 分页查询操作日志
     *
     * @param page         页码
     * @param pageSize     每页条数
     * @param module       模块筛选（可选）
     * @param adminUsername 管理员账号筛选（可选，模糊匹配）
     * @param startDate    开始日期（可选，yyyy-MM-dd）
     * @param endDate      结束日期（可选，yyyy-MM-dd）
     */
    public Map<String, Object> queryLogPage(Integer page, Integer pageSize,
                                             String module, String adminUsername,
                                             String startDate, String endDate) {
        QueryWrapper<OperationLog> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(module)) {
            wrapper.eq("module", module);
        }
        if (StringUtils.isNotBlank(adminUsername)) {
            wrapper.like("admin_username", adminUsername);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        try {
            if (StringUtils.isNotBlank(startDate)) {
                wrapper.ge("created", sdf.parse(startDate));
            }
            if (StringUtils.isNotBlank(endDate)) {
                // 结束日期含当天全天
                wrapper.le("created", new java.util.Date(sdf.parse(endDate).getTime() + 24 * 3600 * 1000 - 1));
            }
        } catch (ParseException e) {
            // 日期格式非法时忽略筛选
        }

        wrapper.orderByDesc("created");

        IPage<OperationLog> logPage = this.operationLogMapper.selectPage(new Page<>(page, pageSize), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", logPage.getTotal());
        result.put("list", logPage.getRecords());
        return result;
    }

    /**
     * 查询日志详情
     */
    public OperationLog queryLogDetail(Long id) {
        return this.operationLogMapper.selectById(id);
    }

    /**
     * 导出日志列表（返回全部匹配数据，不分页）
     */
    public List<OperationLog> exportLogs(String module, String adminUsername,
                                         String startDate, String endDate) {
        QueryWrapper<OperationLog> wrapper = buildLogFilterWrapper(module, adminUsername, startDate, endDate);
        wrapper.orderByDesc("created");
        return this.operationLogMapper.selectList(wrapper);
    }

    /**
     * 构建日志过滤条件（分页/导出共用）
     */
    private QueryWrapper<OperationLog> buildLogFilterWrapper(String module, String adminUsername,
                                                              String startDate, String endDate) {
        QueryWrapper<OperationLog> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(module)) {
            wrapper.eq("module", module);
        }
        if (StringUtils.isNotBlank(adminUsername)) {
            wrapper.like("admin_username", adminUsername);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        try {
            if (StringUtils.isNotBlank(startDate)) {
                wrapper.ge("created", sdf.parse(startDate));
            }
            if (StringUtils.isNotBlank(endDate)) {
                // 结束日期含当天全天
                wrapper.le("created", new java.util.Date(sdf.parse(endDate).getTime() + 24 * 3600 * 1000 - 1));
            }
        } catch (ParseException e) {
            // 日期格式非法时忽略筛选
        }
        return wrapper;
    }
}