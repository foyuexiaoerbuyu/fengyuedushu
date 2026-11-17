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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 接口鉴权拦截器，按路径前缀分流校验：
 * <ul>
 *   <li>{@code /api/admin/**}：始终校验管理口令 X-Admin-Token（或 ?adminToken=），
 *       不受 auth-enabled 影响；管理口令未配置时一律拒绝，避免空口令放行。</li>
 *   <li>其余（{@code /api/v1/**}）：auth-enabled=true 时校验客户端 token X-FY-Token（或 ?token=）。</li>
 * </ul>
 */
@Component
public class TokenInterceptor implements HandlerInterceptor {

    private static final String ADMIN_PREFIX = "/api/admin";

    private final FyReaderProperties properties;
    private final ObjectMapper objectMapper;

    public TokenInterceptor(FyReaderProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = (contextPath != null && !contextPath.isEmpty() && uri != null && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
        if (path != null && path.startsWith(ADMIN_PREFIX)) {
            return checkAdmin(request, response);
        }
        return checkClient(request, response);
    }

    /** 管理端：口令未配置视为未开启，任何请求都拒绝 */
    private boolean checkAdmin(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String adminToken = request.getHeader("X-Admin-Token");
        if (!StringUtils.hasText(adminToken)) {
            adminToken = request.getParameter("adminToken");
        }
        if (matches(properties.getAdminToken(), adminToken)) {
            return true;
        }
        return reject(response, "管理口令无效或缺失");
    }

    /** 客户端：auth-enabled=true 时才校验 */
    private boolean checkClient(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!properties.isAuthEnabled()) {
            return true;
        }
        String token = request.getHeader("X-FY-Token");
        if (!StringUtils.hasText(token)) {
            token = request.getParameter("token");
        }
        if (matches(properties.getToken(), token)) {
            return true;
        }
        return reject(response, "token 无效或缺失");
    }

    /** 定长比较，避免逐字符短路比较带来的时序差异 */
    private boolean matches(String expected, String actual) {
        if (!StringUtils.hasText(expected) || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private boolean reject(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(
                    Result.fail(ResultCode.UNAUTHORIZED, message)));
        }
        return false;
    }
}
