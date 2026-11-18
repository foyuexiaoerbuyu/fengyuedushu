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
                .excludePathPatterns(
                        // 健康检查：客户端设置页的连接检测从不带 token，必须对外开放（修复必 401 的断点）
                        "/api/v1/health",
                        // 日志说明接口同样用于排障展示
                        "/api/v1/health/**",
                        // App 更新走系统浏览器下载，浏览器不会带自定义请求头，下载接口必须免鉴权
                        "/api/v1/app/apk",
                        // 登录 / 注册 / 服务能力查询：本身就是用来获取身份的入口，必须匿名可达
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/config");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/index.html");
    }
}
