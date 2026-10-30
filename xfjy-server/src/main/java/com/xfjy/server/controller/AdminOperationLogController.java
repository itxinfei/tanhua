package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.service.AdminOperationLogService;
import com.xfjy.server.pojo.OperationLog;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * 后台操作日志查询Controller
 */
@RestController
@RequireRole({"ADMIN", "OPERATOR"})
@RequestMapping("admin/operation-logs")
public class AdminOperationLogController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminOperationLogController.class);

    @Autowired
    private AdminOperationLogService adminOperationLogService;

    /**
     * 分页查询操作日志
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> queryLogPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String adminUsername,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            Map<String, Object> result = this.adminOperationLogService.queryLogPage(
                    page, pageSize, module, adminUsername, startDate, endDate);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 查询日志详情
     */
    @GetMapping("{id}")
    public ResponseEntity<OperationLog> queryLogDetail(@PathVariable("id") Long id) {
        try {
            OperationLog log = this.adminOperationLogService.queryLogDetail(id);
            if (log == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(log);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 导出日志列表（CSV）
     */
    @GetMapping("/export")
    public void exportLogs(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String adminUsername,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) throws IOException {
        try {
            List<OperationLog> logs = this.adminOperationLogService.exportLogs(module, adminUsername, startDate, endDate);

            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=operation-logs.csv");
            PrintWriter writer = response.getWriter();

            // CSV 头
            writer.println("ID,操作人,模块,动作,目标ID,详情,IP,操作时间");

            // 数据行
            for (OperationLog log : logs) {
                String created = log.getCreated() != null ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(log.getCreated()) : "";
                writer.println(String.join(",",
                    String.valueOf(log.getId()),
                    log.getAdminUsername(),
                    log.getModule(),
                    log.getAction(),
                    String.valueOf(log.getTargetId()),
                    log.getDetail() != null ? log.getDetail().replace(",", " ") : "",
                    log.getIp() != null ? log.getIp() : "",
                    created
                ));
            }
            writer.flush();
        } catch (Exception e) {
            LOGGER.error("导出日志失败", e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.getWriter().println("导出失败: " + e.getMessage());
        }
    }
}