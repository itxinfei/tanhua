package com.xfjy.server.config;

import com.xfjy.server.interceptor.AdminTokenInterceptor;
import com.xfjy.server.interceptor.TokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private TokenInterceptor tokenInterceptor;

    @Autowired
    private AdminTokenInterceptor adminTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //C端用户Token认证拦截器（排除管理后台路径与监控端点）
        registry.addInterceptor(this.tokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/admin/**", "/error", "/test-media/**", "/actuator/**");

        //管理后台Token拦截器（排除登录接口）
        registry.addInterceptor(this.adminTokenInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login");
    }

    /**
     * 本地测试素材静态资源映射：/test-media/** → test-assets/ 目录
     * 用于开发环境提供测试头像/图片/视频（生产环境应使用 OSS）
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/test-media/**")
                .addResourceLocations("file:d:/MyCode/mytanhua/test-assets/");
    }
}