package com.fyreader.service.controller;

import com.fyreader.service.auth.AuthContext;
import com.fyreader.service.common.BusinessException;
import com.fyreader.service.entity.User;
import com.fyreader.service.service.AuthService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端账号管理接口，供后台 /index.html 的「账号管理」页签使用。
 *
 * <p>整段受 {@code TokenInterceptor} 的 /api/admin 前缀保护：既接受固定管理口令，
 * 也接受角色为 ADMIN 的账号登录 token。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    /** 分页查询账号，keyword 同时匹配用户名与昵称 */
    @GetMapping
    public Map<String, Object> list(@RequestParam(value = "keyword", required = false) String keyword,
                                    @RequestParam(value = "page", defaultValue = "1") int page,
                                    @RequestParam(value = "size", defaultValue = "20") int size) {
        return authService.listUsers(keyword, page, size);
    }

    /** 新建账号；role 传 ADMIN 则创建管理员 */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        User user = authService.createUser(str(body, "username"), str(body, "password"),
                str(body, "nickname"), str(body, "role"));
        return authService.toView(user);
    }

    /**
     * 重置密码：请求体可带 password，不传则服务端随机生成。
     * 返回结果中的 password 为明文，仅此一次返回，供管理员转告用户。
     */
    @PostMapping("/{id}/password")
    public Map<String, Object> resetPassword(@PathVariable("id") String id,
                                             @RequestBody(required = false) Map<String, Object> body) {
        String password = str(body, "password");
        boolean generated = !StringUtils.hasText(password);
        if (generated) {
            password = authService.generatePassword(12);
        }
        authService.resetPassword(id, password);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reset", true);
        data.put("generated", generated);
        data.put("password", password);
        return data;
    }

    /** 启用 / 停用账号，body: {"status": 1|0} */
    @PostMapping("/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable("id") String id,
                                            @RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("status");
        Integer status = raw == null ? null : Integer.valueOf(String.valueOf(raw));
        authService.updateStatus(id, status);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("updated", true);
        data.put("status", status);
        return data;
    }

    /** 删除账号；不允许删除自己与最后一个管理员 */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable("id") String id) {
        authService.deleteUser(AuthContext.currentUserId(), id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", true);
        return data;
    }

    /** 随机密码生成，供后台「新建账号/重置密码」表单填充 */
    @GetMapping("/generate-password")
    public Map<String, Object> generatePassword(@RequestParam(value = "length", defaultValue = "12") int length) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("password", authService.generatePassword(length));
        return data;
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
