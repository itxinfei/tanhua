package com.xfjy.server.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理端角色权限注解。
 * <p>
 * 标记在 Controller 类或方法上，由 {@link com.xfjy.server.interceptor.AdminTokenInterceptor}
 * 校验当前登录管理员的角色是否在允许列表内。
 * 规则：方法级注解优先于类级注解；无任何注解表示仅需登录（不限制角色）。
 * <p>
 * 角色取值：SUPER_ADMIN / ADMIN / OPERATOR
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * 允许访问的角色列表
     */
    String[] value();
}
