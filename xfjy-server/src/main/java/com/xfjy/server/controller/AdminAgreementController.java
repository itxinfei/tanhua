package com.xfjy.server.controller;

import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.pojo.Agreement;
import com.xfjy.server.service.AdminAgreementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台协议管理 Controller（管理员 CRUD）
 */
@RestController
@RequireRole("ADMIN")
@RequestMapping("admin/agreements")
public class AdminAgreementController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAgreementController.class);

    @Autowired
    private AdminAgreementService adminAgreementService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> queryAgreementPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        try {
            return ResponseEntity.ok(this.adminAgreementService.queryAgreementPage(page, pageSize, type, status));
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("{id}")
    public ResponseEntity<Agreement> queryAgreementDetail(@PathVariable("id") Long id) {
        try {
            Agreement agreement = this.adminAgreementService.queryAgreementDetail(id);
            if (agreement == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(agreement);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    public ResponseEntity<Agreement> createAgreement(@RequestBody Map<String, String> param) {
        try {
            Agreement agreement = this.adminAgreementService.createAgreement(
                    param.get("type"), param.get("title"), param.get("content"));
            return ResponseEntity.ok(agreement);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("{id}")
    public ResponseEntity<Void> updateAgreement(@PathVariable("id") Long id,
                                                @RequestBody Map<String, String> param) {
        try {
            Boolean success = this.adminAgreementService.updateAgreement(
                    id, param.get("type"), param.get("title"), param.get("content"));
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("{id}/publish")
    public ResponseEntity<Void> publishAgreement(@PathVariable("id") Long id) {
        try {
            Boolean success = this.adminAgreementService.publishAgreement(id);
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("{id}/offline")
    public ResponseEntity<Void> offlineAgreement(@PathVariable("id") Long id) {
        try {
            Boolean success = this.adminAgreementService.offlineAgreement(id);
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> deleteAgreement(@PathVariable("id") Long id) {
        try {
            Boolean success = this.adminAgreementService.deleteAgreement(id);
            return success ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
