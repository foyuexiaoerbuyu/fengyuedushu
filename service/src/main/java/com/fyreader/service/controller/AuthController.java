package com.fyreader.service.controller;

import com.fyreader.service.auth.AuthContext;
import com.fyreader.service.common.BusinessException;
import com.fyreader.service.config.FyReaderProperties;
import com.fyreader.service.entity.User;
import com.fyreader.service.service.AuthService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 账号接口：注册 / 登录 / 当前用户 / 登出。
 * <p>登录与注册在 WebConfig 中免鉴权；/me 与 /logout 需要有效 token。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final FyReaderProperties properties;

    public AuthController(AuthService authService, FyReaderProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    /** 注册（是否开放由 fyreader.allow-register 控制），成功后直接返回 token */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        String username = str(body, "username");
        String password = str(body, "password");
        String nickname = str(body, "nickname");
        return authService.register(username, password, nickname);
    }

    /** 登录，返回 token 与用户信息 */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String username = str(body, "username");
        String password = str(body, "password");
        return authService.login(username, password);
    }

    /** 当前登录用户（管理员账号亦可查询） */
    @GetMapping("/me")
    public Map<String, Object> me() {
        AuthContext context = AuthContext.current();
        if (context.isAnonymous() || !StringUtils.hasText(context.getUserId())) {
            throw new BusinessException(401, "登录已失效，请重新登录");
        }
        User user = authService.requireUser(currentToken());
        return authService.toView(user);
    }

    /** 修改自己的密码：需提供原密码，成功后其它设备会话失效 */
    @PostMapping("/password")
    public Map<String, Object> changePassword(@RequestBody Map<String, Object> body) {
        authService.changePassword(currentToken(), str(body, "oldPassword"), str(body, "newPassword"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("changed", true);
        return data;
    }

    /** 登出：删除当前会话记录，token 立即失效 */
    @PostMapping("/logout")
    public Map<String, Object> logout() {
        authService.logout(currentToken());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("loggedOut", true);
        return data;
    }

    /** 服务端鉴权能力查询，供前端决定是否展示注册入口（免鉴权） */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("allowRegister", properties.isAllowRegister());
        data.put("sessionDays", properties.getSessionDays());
        data.put("passwordMinLength", properties.getPasswordMinLength());
        return data;
    }

    /** 仅从请求头取 token（?token= 兜底已关闭） */
    private String currentToken() {
        String token = AuthContext.currentToken();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(401, "缺少登录凭证");
        }
        return token;
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
