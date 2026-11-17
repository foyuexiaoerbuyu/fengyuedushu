package com.fyreader.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyreader.service.common.Result;
import com.fyreader.service.common.ResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * 客户端接口 token 校验拦截器（仅作用于 /api/v1/**，是否启用由配置决定）。
 */
@Component
public class TokenInterceptor implements HandlerInterceptor {

    private final FyReaderProperties properties;
    private final ObjectMapper objectMapper;

    public TokenInterceptor(FyReaderProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!properties.isAuthEnabled()) {
            return true;
        }
        String token = request.getHeader("X-FY-Token");
        if (!StringUtils.hasText(token)) {
            token = request.getParameter("token");
        }
        if (properties.getToken() != null && properties.getToken().equals(token)) {
            return true;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(
                    Result.fail(ResultCode.UNAUTHORIZED, "token 无效或缺失")));
        }
        return false;
    }
}
