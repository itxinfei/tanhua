package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.service.AdminOperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}