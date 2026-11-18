package com.fyreader.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyreader.service.auth.AuthContext;
import com.fyreader.service.common.Result;
import com.fyreader.service.common.ResultCode;
import com.fyreader.service.service.AuthService;
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
 *   <li>其余（{@code /api/v1/**}）：识别三级身份并写入 {@link AuthContext}——
 *       匿名 / 登录用户 / 部署者（共享口令或管理口令），具体可见性由业务层判定。
 *       health、app/apk、auth 登录注册等入口由 WebConfig 免鉴权放行。</li>
 * </ul>
 */
@Component
public class TokenInterceptor implements HandlerInterceptor {

    private static final String ADMIN_PREFIX = "/api/admin";
    private static final String CLIENT_TOKEN_HEADER = "X-FY-Token";

    private final FyReaderProperties properties;
    private final ObjectMapper objectMapper;
    private final AuthService authService;

    public TokenInterceptor(FyReaderProperties properties, ObjectMapper objectMapper, AuthService authService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.authService = authService;
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

    /**
     * 管理端两种入口，满足其一即放行：
     * <ol>
     *   <li>固定管理口令（X-Admin-Token 头或 adminToken 查询参数），兼容旧部署方式与环境变量注入；</li>
     *   <li>账号体系登录后签发的 token（X-FY-Token 或 X-Admin-Token 头），要求该账号角色为 ADMIN。</li>
     * </ol>
     */
    private boolean checkAdmin(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String adminToken = request.getHeader("X-Admin-Token");
        if (!StringUtils.hasText(adminToken)) {
            // 管理后台是服务端托管的 HTML 页面，部分请求难以带自定义头，保留查询参数通道
            adminToken = request.getParameter("adminToken");
        }
        if (matches(properties.getAdminToken(), adminToken)) {
            AuthContext.set(AuthContext.admin(adminToken));
            return true;
        }
        String sessionToken = request.getHeader(CLIENT_TOKEN_HEADER);
        if (!StringUtils.hasText(sessionToken)) {
            sessionToken = adminToken;
        }
        if (StringUtils.hasText(sessionToken)) {
            AuthContext ctx = authService.resolve(sessionToken);
            if (ctx != null && ctx.isAdmin()) {
                AuthContext.set(ctx);
                return true;
            }
            if (ctx != null) {
                return reject(response, HttpStatus.FORBIDDEN.value(), "该账号没有管理权限");
            }
        }
        return reject(response, HttpStatus.UNAUTHORIZED.value(), "管理口令无效或缺失");
    }

    /**
     * 客户端三级身份：
     * <ol>
     *   <li>无 token → 匿名放行，业务层只返回公开书（visibility=PUBLIC）</li>
     *   <li>token 命中共享口令 → 部署者身份，可读全部书籍（兼容旧版单机客户端）</li>
     *   <li>token 命中登录会话 → 登录用户，可读公开书 + 自己名下私有书</li>
     *   <li>token 存在但不匹配以上任一 → 401，提示重新登录</li>
     * </ol>
     * <p>注意：查询参数 ?token= 兜底已关闭，凭证只认请求头，避免 URL 泄露被日志/代理记录。</p>
     */
    private boolean checkClient(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String token = request.getHeader(CLIENT_TOKEN_HEADER);
        if (!properties.isAuthEnabled()) {
            // 调试模式（auth-enabled=false）：一律放行，但若带了凭证仍尝试解析，便于"我的书籍"类接口工作
            AuthContext ctx = StringUtils.hasText(token) ? authService.resolve(token) : null;
            AuthContext.set(ctx == null ? AuthContext.anonymous() : ctx);
            return true;
        }
        if (!StringUtils.hasText(token)) {
            // 匿名访问：放行，由业务层按可见性收敛到公开书
            AuthContext.set(AuthContext.anonymous());
            return true;
        }
        if (matches(properties.getToken(), token)) {
            AuthContext.set(AuthContext.admin(token));
            return true;
        }
        AuthContext ctx = authService.resolve(token);
        if (ctx != null) {
            AuthContext.set(ctx);
            return true;
        }
        return reject(response, "token 无效或已过期，请重新登录");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 线程池复用，必须清理，避免身份串号
        AuthContext.clear();
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
        return reject(response, HttpStatus.UNAUTHORIZED.value(), message);
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(
                    Result.fail(ResultCode.UNAUTHORIZED, message)));
        }
        return false;
    }
}
