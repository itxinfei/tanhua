package com.xfjy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfjy.server.mapper.ReportMapper;
import com.xfjy.server.mapper.UserInfoMapper;
import com.xfjy.server.pojo.Report;
import com.xfjy.server.pojo.UserInfo;
import com.xfjy.server.utils.AdminThreadLocal;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 后台举报处理服务
 */
@Service
public class AdminReportService {

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private UserInfoMapper userInfoMapper;

    /**
     * 分页查询举报列表
     */
    public Map<String, Object> queryReportPage(Integer page, Integer pageSize,
                                                String status, String targetType) {
        QueryWrapper<Report> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(status)) {
            wrapper.eq("status", status);
        }
        if (StringUtils.isNotBlank(targetType)) {
            wrapper.eq("target_type", targetType);
        }
        wrapper.orderByDesc("created");

        IPage<Report> reportPage = this.reportMapper.selectPage(new Page<>(page, pageSize), wrapper);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Report report : reportPage.getRecords()) {
            items.add(convertToItem(report));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", reportPage.getTotal());
        result.put("list", items);
        return result;
    }

    /**
     * 查询举报详情
     */
    public Map<String, Object> queryReportDetail(Long id) {
        Report report = this.reportMapper.selectById(id);
        if (report == null) {
            return null;
        }
        return convertToItem(report);
    }

    /**
     * 处理举报
     *
     * @param id     举报ID
     * @param result RESOLVED-确认违规 / DISMISSED-驳回
     * @param note   处理备注
     */
    public Boolean handleReport(Long id, String result, String note) {
        Report report = this.reportMapper.selectById(id);
        if (report == null || !"PENDING".equals(report.getStatus())) {
            return false;
        }
        report.setStatus(result); //RESOLVED 或 DISMISSED
        report.setHandleResult(note);
        report.setHandler(AdminThreadLocal.get() != null ? AdminThreadLocal.get().getNickname() : "admin");
        report.setHandledAt(new Date());
        return this.reportMapper.updateById(report) > 0;
    }

    /**
     * Report对象转前端Item（合并举报人/被举报人昵称头像）
     */
    private Map<String, Object> convertToItem(Report report) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", report.getId());
        item.put("reporterId", report.getReporterId());
        item.put("targetUserId", report.getTargetUserId());
        item.put("targetType", report.getTargetType());
        item.put("targetId", report.getTargetId());
        item.put("reason", report.getReason());
        item.put("description", report.getDescription());
        item.put("evidence", report.getEvidence() != null ? Arrays.asList(report.getEvidence().split(",")) : Collections.emptyList());
        item.put("status", report.getStatus());
        item.put("handler", report.getHandler());
        item.put("handledAt", report.getHandledAt());
        item.put("handleResult", report.getHandleResult());
        item.put("createdAt", report.getCreated());

        // 举报人信息
        fillUserInfo(item, report.getReporterId(), "reporter");
        // 被举报人信息
        fillUserInfo(item, report.getTargetUserId(), "targetUser");

        return item;
    }

    private void fillUserInfo(Map<String, Object> item, Long userId, String prefix) {
        QueryWrapper<UserInfo> infoWrapper = new QueryWrapper<>();
        infoWrapper.eq("user_id", userId);
        UserInfo userInfo = this.userInfoMapper.selectOne(infoWrapper);
        if (userInfo != null) {
            item.put(prefix + "Nickname", userInfo.getNickName());
            item.put(prefix + "Avatar", userInfo.getLogo());
        } else {
            item.put(prefix + "Nickname", "用户" + userId);
            item.put(prefix + "Avatar", "");
        }
    }
}