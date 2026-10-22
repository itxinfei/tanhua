package com.xfjy.server.interceptor;

import com.xfjy.server.pojo.Admin;
import com.xfjy.server.service.AdminService;
import com.xfjy.server.utils.AdminThreadLocal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 管理后台Token拦截器
 * 校验请求头 Authorization 中的管理员Token（Redis存储）
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

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        Admin admin = this.adminService.queryAdminByToken(token);
        if (admin == null) {
            response.setStatus(401); //无权限
            return false;
        }

        // 存储到当前线程
        AdminThreadLocal.set(admin);
        return true;
    }
}