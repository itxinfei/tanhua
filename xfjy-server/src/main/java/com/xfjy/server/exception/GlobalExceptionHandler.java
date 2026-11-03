package com.xfjy.server.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.xfjy.server.exception.BenefitLimitException;
import com.xfjy.server.exception.SwipeLimitException;
import com.xfjy.server.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器：兜底所有 Controller 抛出的异常，返回统一 Result 结构
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常（Controller/Service 显式抛出）
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        LOGGER.warn("[业务异常] code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 处理参数校验异常（@Valid/@Validated）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(400, msg));
    }

    /**
     * 处理滑动次数耗尽异常（HTTP 402 PAYMENT_REQUIRED）
     *
     * <p>返回结构化 data，便于前端区分"业务 400"和"需要付费 402"：
     * 前端拦截到此状态码后跳转 /pages/mine/vip 付费引导页。</p>
     */
    @ExceptionHandler(SwipeLimitException.class)
    public ResponseEntity<Result<SwipeLimitException>> handleSwipeLimit(SwipeLimitException e) {
        LOGGER.warn("[滑动限制] code={}, type={}, usage={}/{}, message={}",
                SwipeLimitException.CODE, e.getLimitType(), e.getCurrentUsage(), e.getMaxLimit(), e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(Result.fail(SwipeLimitException.CODE, e.getMessage()));
    }

    /**
     * 处理权益次数耗尽异常（HTTP 402 PAYMENT_REQUIRED）
     *
     * <p>super_like / advanced_filter 等每日限次权益用尽时抛出。</p>
     */
    @ExceptionHandler(BenefitLimitException.class)
    public ResponseEntity<Result<BenefitLimitException>> handleBenefitLimit(BenefitLimitException e) {
        LOGGER.warn("[权益限制] code={}, service={}, message={}",
                BenefitLimitException.CODE, e.getServiceCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(Result.fail(BenefitLimitException.CODE, e.getMessage()));
    }

    /**
     * 处理 404（无匹配路由）
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(404, "接口不存在"));
    }

    /**
     * 处理未登录 / 登录态失效（HTTP 401）
     *
     * <p>仅捕获 Sa-Token 主动抛出 {@code checkLogin()} 类异常；当前鉴权链中的
     * {@code TokenInterceptor} / {@code AdminTokenInterceptor} 是直接
     * {@code response.setStatus(401)} 并短路返回，不进入本处理器。</p>
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException e) {
        LOGGER.warn("[未登录] type={}, loginType={}", e.getType(), e.getLoginType());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(401, "未登录或登录已失效"));
    }

    /**
     * 处理无权限（HTTP 403）
     *
     * <p>同理，{@code @RequireRole} 角色不符由 {@code AdminTokenInterceptor}
     * 直接返回 403，本处理器只兜底 Sa-Token 主动抛出的角色/权限异常。</p>
     */
    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    public ResponseEntity<Result<Void>> handleNoPermission(Exception e) {
        LOGGER.warn("[无权限] {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(403, "无权限访问该资源"));
    }

    /**
     * 处理数据冲突（HTTP 409）：唯一键 / 唯一索引冲突
     *
     * <p>数据库唯一约束被违反时由 Spring 抛出。注意 user-service 的
     * {@code UserInfoApiImpl} 内部已 catch 该异常做幂等处理，server 模块当前
     * 无直接抛出点，此处作为兜底映射。</p>
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException e) {
        LOGGER.warn("[数据冲突] {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.fail(409, "数据冲突或重复操作"));
    }

    /**
     * 处理下游服务不可用（HTTP 503）
     *
     * <p>{@code HttpServiceProxyConfig} 通过 RestClient 调用域服务，连接失败 /
     * 超时抛出 {@code ResourceAccessException}，统一映射为 503 而非 500。</p>
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Result<Void>> handleDownstreamUnavailable(ResourceAccessException e) {
        LOGGER.error("[下游不可用] {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Result.fail(503, "下游服务暂时不可用，请稍后再试"));
    }

    /**
     * 处理所有未捕获异常（兜底）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        LOGGER.error("[全局异常] 未捕获异常: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, "服务器内部错误，稍后再试"));
    }
}
