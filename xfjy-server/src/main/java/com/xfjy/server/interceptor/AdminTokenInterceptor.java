package com.xfjy.server.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.xfjy.server.annotation.RequireRole;
import com.xfjy.server.pojo.Admin;
import com.xfjy.server.service.AdminService;
import com.xfjy.server.utils.AdminThreadLocal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;

/**
 * 管理后台Token拦截器（Sa-Token校验，token存于Sa-Token存储中）。
 * <p>
 * 校验顺序：登录态 → 账号存在且未禁用 → {@link RequireRole} 角色权限。
 */
@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    @Autowired
    private AdminService adminService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Sa-Token 从请求头读取并校验token（未登录返回null，未抛异常）
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            response.setStatus(401); //未登录
            return false;
        }

        Admin admin = this.adminService.queryAdminById(Long.valueOf(loginId.toString()));
        if (admin == null || "DISABLED".equals(admin.getStatus())) {
            response.setStatus(401);
            return false;
        }

        // 角色权限校验：方法级 @RequireRole 优先，其次类级；无注解则仅需登录
        RequireRole requireRole = resolveRequireRole(handler);
        if (requireRole != null) {
            boolean allowed = Arrays.asList(requireRole.value()).contains(admin.getRole());
            if (!allowed) {
                response.setStatus(403); //角色不符
                return false;
            }
        }

        // 存储到当前线程
        AdminThreadLocal.set(admin);
        return true;
    }

    /**
     * 解析处理器上的角色权限注解（方法级优先，类级兜底）
     */
    private RequireRole resolveRequireRole(Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return null;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireRole methodRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (methodRole != null) {
            return methodRole;
        }
        return handlerMethod.getBeanType().getAnnotation(RequireRole.class);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理ThreadLocal，防止线程池内存泄漏
        AdminThreadLocal.remove();
    }
}
