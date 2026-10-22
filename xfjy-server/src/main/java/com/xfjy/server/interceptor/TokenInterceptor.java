package com.xfjy.server.interceptor;

import com.xfjy.server.pojo.User;
import com.xfjy.server.service.UserService;
import com.xfjy.server.utils.NoAuthorization;
import com.xfjy.server.utils.UserThreadLocal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TokenInterceptor implements HandlerInterceptor {

    @Autowired
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //判断，请求的方法是否包含@NoAuthorization ，如果包含了，就不需要做处理
        if(handler instanceof HandlerMethod){
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            NoAuthorization annotation = handlerMethod.getMethod().getAnnotation(NoAuthorization.class);
            if(annotation != null){
                return true;
            }
        }

        String token = request.getHeader("Authorization");
        User user = this.userService.queryUserByToken(token);
        if(null == user){
            response.setStatus(401); //无权限
            return false;
        }

        // 存储到当前的线程中
        UserThreadLocal.set(user);
        return true;
    }
}
