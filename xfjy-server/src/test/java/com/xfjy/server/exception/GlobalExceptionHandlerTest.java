package com.xfjy.server.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.xfjy.server.response.Result;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * GlobalExceptionHandler 新增错误码映射的最小单测。
 *
 * <p>直接调用处理器方法，不启动 Spring 上下文、不依赖数据库/Redis，
 * 只验证「异常类型 → HTTP 状态码 → Result.code」三者一致。</p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notLoginMapsTo401() {
        NotLoginException e = new NotLoginException("admin", NotLoginException.NOT_TOKEN, "");

        ResponseEntity<Result<Void>> response = this.handler.handleNotLogin(e);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Result<Void> body = response.getBody();
        assertNotNull(body);
        assertEquals(401, body.getCode());
    }

    @Test
    void notRoleMapsTo403() {
        ResponseEntity<Result<Void>> response = this.handler.handleNoPermission(new NotRoleException("USER"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getCode());
    }

    @Test
    void notPermissionMapsTo403() {
        ResponseEntity<Result<Void>> response =
                this.handler.handleNoPermission(new NotPermissionException("report:view"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getCode());
    }

    @Test
    void duplicateKeyMapsTo409() {
        ResponseEntity<Result<Void>> response = this.handler.handleDuplicateKey(new DuplicateKeyException("duplicate"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getCode());
    }

    @Test
    void downstreamUnavailableMapsTo503() {
        ResponseEntity<Result<Void>> response =
                this.handler.handleDownstreamUnavailable(new ResourceAccessException("connect timed out"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(503, response.getBody().getCode());
    }
}
