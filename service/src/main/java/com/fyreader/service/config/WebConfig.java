package com.fyreader.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：静态首页、客户端与管理端接口鉴权拦截。
 * <p>服务与管理页同源部署（同一端口），因此不再全局放开 CORS。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TokenInterceptor tokenInterceptor;

    public WebConfig(TokenInterceptor tokenInterceptor) {
        this.tokenInterceptor = tokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 客户端与管理端都要过拦截器，由拦截器按路径前缀分别校验 X-FY-Token / X-Admin-Token
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/api/v1/**", "/api/admin/**")
                // App 更新走系统浏览器下载，浏览器不会带自定义请求头，下载接口必须免鉴权
                .excludePathPatterns("/api/v1/app/apk");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/index.html");
    }
}
