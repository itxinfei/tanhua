package com.xfjy.server.controller;

import com.xfjy.server.pojo.Agreement;
import com.xfjy.server.service.AdminAgreementService;
import com.xfjy.server.utils.NoAuthorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * C 端协议读取 Controller（匿名可访问，供 App 登录前展示）
 */
@RestController
@RequestMapping("agreements")
public class AppAgreementController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppAgreementController.class);

    @Autowired
    private AdminAgreementService adminAgreementService;

    /**
     * 获取某类型当前已发布的协议正文
     *
     * @param type USER(用户协议) / PRIVACY(隐私政策)
     */
    @NoAuthorization
    @GetMapping("{type}")
    public ResponseEntity<Agreement> queryPublished(@PathVariable("type") String type) {
        try {
            Agreement agreement = this.adminAgreementService.queryPublishedAgreement(type);
            if (agreement == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(agreement);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
