package com.xfjy.server.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作日志上下文：在同一次请求内，让 Service 把业务级变更摘要传递给
 * {@code OperationLogAspect}，切面据此填充 {@code detail}（字段级 old→new）。
 *
 * <p>基于 request attribute 实现：Service 与切面在同一请求线程同步执行，
 * 请求结束后随 request 一并销毁，无需手动清理，也无 ThreadLocal 泄漏风险。</p>
 */
public final class OperationLogContext {

    private static final String ATTR_DETAIL = OperationLogContext.class.getName() + ".DETAIL";

    private OperationLogContext() {
    }

    /** 写入本次写操作的自定义变更摘要 */
    public static void setDetail(String detail) {
        HttpServletRequest request = currentRequest();
        if (request != null && detail != null) {
            request.setAttribute(ATTR_DETAIL, detail);
        }
    }

    /** 读取变更摘要；未设置时返回 null，切面回退为默认的「方法名 + URI」 */
    public static String getDetail() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(ATTR_DETAIL);
        return value == null ? null : value.toString();
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }
}
