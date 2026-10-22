package com.xfjy.server.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.xfjy.server.pojo.Admin;
import com.xfjy.server.service.AdminService;
import com.xfjy.server.utils.AdminThreadLocal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 管理后台Token拦截器（Sa-Token校验，token存于Sa-Token存储中）
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
            response.setStatus(401); //无权限
            return false;
        }

        Admin admin = this.adminService.queryAdminById(Long.valueOf(loginId.toString()));
        if (admin == null || "DISABLED".equals(admin.getStatus())) {
            response.setStatus(401);
            return false;
        }

        // 存储到当前线程
        AdminThreadLocal.set(admin);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理ThreadLocal，防止线程池内存泄漏
        AdminThreadLocal.remove();
    }
}
