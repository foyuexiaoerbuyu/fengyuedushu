package com.fyreader.service.service;

import com.fyreader.service.auth.AuthContext;
import com.fyreader.service.common.BusinessException;
import com.fyreader.service.config.FyReaderProperties;
import com.fyreader.service.entity.User;
import com.fyreader.service.entity.UserSession;
import com.fyreader.service.mapper.UserMapper;
import com.fyreader.service.mapper.UserSessionMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 账号与会话服务：注册、登录、token 校验、登出。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>密码只存 BCrypt 摘要，永不明文落库、永不返回前端；</li>
 *   <li>登录 token 为 32 字节 / 43 字符 Base64URL 高熵随机串，落 {@code t_user_session}，可吊销；</li>
 *   <li>token 有效期由 {@code fyreader.session-days} 控制，默认 7 天；</li>
 *   <li>是否允许自助注册由 {@code fyreader.allow-register} 控制，默认允许。</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 32;
    private static final int PASSWORD_MAX = 64;
    private static final int NICKNAME_MAX = 32;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_@.-]+$");
    private static final int TOKEN_BYTES = 32;
    private static final char[] B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    private final UserMapper userMapper;
    private final UserSessionMapper sessionMapper;
    private final FyReaderProperties properties;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserMapper userMapper, UserSessionMapper sessionMapper, FyReaderProperties properties) {
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
        this.properties = properties;
    }

    /** 注册并直接登录，返回 { token, expiresAt, expiresIn, user } */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(String username, String password, String nickname) {
        if (!properties.isAllowRegister()) {
            throw new BusinessException(403, "服务端已关闭自助注册，请联系管理员开通账号");
        }
        String name = normalizeUsername(username);
        String pwd = validatePassword(password);
        if (userMapper.selectByUsername(name) != null) {
            throw new BusinessException(409, "用户名已被占用: " + name);
        }
        User user = new User();
        user.setId("u_" + UUID.randomUUID().toString().replace("-", ""));
        user.setUsername(name);
        user.setPasswordHash(passwordEncoder.encode(pwd));
        String nick = StringUtils.hasText(nickname) ? nickname.trim() : name;
        if (nick.length() > NICKNAME_MAX) {
            nick = nick.substring(0, NICKNAME_MAX);
        }
        user.setNickname(nick);
        user.setStatus(User.STATUS_ENABLED);
        user.setRole(User.ROLE_USER);
        userMapper.insert(user);
        return issueToken(userMapper.selectById(user.getId()));
    }

    /** 登录，返回 { token, expiresAt, expiresIn, user } */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> login(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw BusinessException.badRequest("用户名和密码不能为空");
        }
        User user = userMapper.selectByUsername(username.trim());
        // 用户不存在与密码错误返回同一提示，避免用户名枚举
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!user.isEnabled()) {
            throw new BusinessException(403, "账号已被停用");
        }
        // 顺带清理过期会话，避免会话表无限增长
        sessionMapper.deleteExpired(new Date());
        return issueToken(user);
    }

    /** 吊销指定 token */
    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            sessionMapper.deleteByToken(token);
        }
    }

    /** 注销该用户全部会话（修改密码等场景备用） */
    public void logoutAll(String userId) {
        if (StringUtils.hasText(userId)) {
            sessionMapper.deleteByUserId(userId);
        }
    }

    /** 按 token 解析身份；返回 null 表示 token 无效/已过期/用户已停用 */
    public AuthContext resolve(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        UserSession session = sessionMapper.selectByToken(token);
        if (session == null) {
            return null;
        }
        if (session.isExpired()) {
            sessionMapper.deleteByToken(token);
            return null;
        }
        User user = userMapper.selectById(session.getUserId());
        if (user == null || !user.isEnabled()) {
            sessionMapper.deleteByToken(token);
            return null;
        }
        return user.isAdmin()
                ? AuthContext.ofAdminUser(user.getId(), user.getUsername(), token)
                : AuthContext.ofUser(user.getId(), user.getUsername(), token);
    }

    /** 当前 token 对应的用户（/me 用），无效则抛 401 */
    public User requireUser(String token) {
        AuthContext context = resolve(token);
        if (context == null) {
            throw new BusinessException(401, "登录已失效，请重新登录");
        }
        User user = userMapper.selectById(context.getUserId());
        if (user == null) {
            throw new BusinessException(401, "登录已失效，请重新登录");
        }
        return user;
    }

    /** 幂等查询：用户名是否可用 */
    public boolean usernameExists(String username) {
        return StringUtils.hasText(username) && userMapper.selectByUsername(username.trim()) != null;
    }

    /** 用户对外视图，绝不包含密码摘要 */
    public Map<String, Object> toView(User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("username", user.getUsername());
        view.put("nickname", StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        view.put("role", StringUtils.hasText(user.getRole()) ? user.getRole() : User.ROLE_USER);
        view.put("status", user.getStatus());
        view.put("createTime", user.getCreateTime());
        view.put("admin", user.isAdmin());
        return view;
    }

    // ================= 密码与账号管理 =================

    /**
     * 修改自己的密码：校验旧密码 → 校验新密码强度 → 落库 → 吊销其他会话（当前会话保留）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(String token, String oldPassword, String newPassword) {
        User user = requireUser(token);
        if (!StringUtils.hasText(oldPassword)) {
            throw BusinessException.badRequest("原密码不能为空");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(400, "原密码不正确");
        }
        String pwd = validatePassword(newPassword);
        if (passwordEncoder.matches(pwd, user.getPasswordHash())) {
            throw new BusinessException(400, "新密码不能与原密码相同");
        }
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(pwd));
        // 修改密码后其它设备的会话立即失效，当前会话保留，避免本人被踢下线
        sessionMapper.deleteByUserIdExcept(user.getId(), token);
    }

    /** 管理员创建账号 */
    @Transactional(rollbackFor = Exception.class)
    public User createUser(String username, String password, String nickname, String role) {
        String name = normalizeUsername(username);
        String pwd = validatePassword(password);
        if (userMapper.selectByUsername(name) != null) {
            throw new BusinessException(409, "用户名已被占用: " + name);
        }
        User user = new User();
        user.setId("u_" + UUID.randomUUID().toString().replace("-", ""));
        user.setUsername(name);
        user.setPasswordHash(passwordEncoder.encode(pwd));
        String nick = StringUtils.hasText(nickname) ? nickname.trim() : name;
        if (nick.length() > NICKNAME_MAX) {
            nick = nick.substring(0, NICKNAME_MAX);
        }
        user.setNickname(nick);
        user.setStatus(User.STATUS_ENABLED);
        user.setRole(User.ROLE_ADMIN.equalsIgnoreCase(role) ? User.ROLE_ADMIN : User.ROLE_USER);
        userMapper.insert(user);
        return userMapper.selectById(user.getId());
    }

    /** 管理员重置密码（无需旧密码），并吊销该账号全部会话 */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String userId, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "账号不存在");
        }
        String pwd = validatePassword(newPassword);
        userMapper.updatePassword(userId, passwordEncoder.encode(pwd));
        sessionMapper.deleteByUserId(userId);
    }

    /** 管理员启用/停用账号，停用同时吊销全部会话 */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "账号不存在");
        }
        int target = (status != null && status == User.STATUS_ENABLED) ? User.STATUS_ENABLED : User.STATUS_DISABLED;
        userMapper.updateStatus(userId, target);
        if (target == User.STATUS_DISABLED) {
            sessionMapper.deleteByUserId(userId);
        }
    }

    /** 管理员删除账号：不允许删除最后一个管理员，不允许删除自己 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(String operatorUserId, String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "账号不存在");
        }
        if (userId.equals(operatorUserId)) {
            throw new BusinessException(400, "不能删除当前登录的账号");
        }
        if (user.isAdmin() && userMapper.countByRole(User.ROLE_ADMIN) <= 1) {
            throw new BusinessException(400, "至少保留一个管理员账号");
        }
        sessionMapper.deleteByUserId(userId);
        userMapper.deleteById(userId);
    }

    /** 管理员分页查询账号 */
    public Map<String, Object> listUsers(String keyword, int page, int size) {
        int p = Math.max(page, 1);
        int s = size > 0 ? Math.min(size, properties.getMaxPageSize()) : properties.getDefaultPageSize();
        long total = userMapper.countList(keyword);
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (User user : userMapper.selectList(keyword, (p - 1) * s, s)) {
            items.add(toView(user));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("page", p);
        data.put("size", s);
        return data;
    }

    /** 生成随机密码：大小写字母 + 数字，避开易混淆字符，保证通过强度校验 */
    public String generatePassword(int length) {
        final String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String lower = "abcdefghijkmnpqrstuvwxyz";
        final String digits = "23456789";
        final String all = upper + lower + digits;
        int len = Math.max(length, 10);
        StringBuilder sb = new StringBuilder(len);
        sb.append(upper.charAt(secureRandom.nextInt(upper.length())));
        sb.append(lower.charAt(secureRandom.nextInt(lower.length())));
        sb.append(digits.charAt(secureRandom.nextInt(digits.length())));
        while (sb.length() < len) {
            sb.append(all.charAt(secureRandom.nextInt(all.length())));
        }
        // 打乱前三位固定顺序
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }


    private Map<String, Object> issueToken(User user) {
        int days = properties.getSessionDays() > 0 ? properties.getSessionDays() : 7;
        Date expire = new Date(System.currentTimeMillis() + days * 24L * 3600L * 1000L);
        String token = generateToken();
        UserSession session = new UserSession();
        session.setToken(token);
        session.setUserId(user.getId());
        session.setExpireTime(expire);
        sessionMapper.insert(session);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("expiresAt", expire.getTime());
        payload.put("expiresIn", days * 24L * 3600L);
        payload.put("user", toView(user));
        return payload;
    }

    /** 32 字节随机数 → 43 字符 Base64URL（无填充），熵约 256 bit */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(43);
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 6) {
                bits -= 6;
                sb.append(B64[(buffer >> bits) & 0x3F]);
            }
        }
        if (bits > 0) {
            sb.append(B64[(buffer << (6 - bits)) & 0x3F]);
        }
        return sb.toString();
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw BusinessException.badRequest("用户名不能为空");
        }
        String name = username.trim();
        if (name.length() < USERNAME_MIN || name.length() > USERNAME_MAX) {
            throw BusinessException.badRequest("用户名长度需为 " + USERNAME_MIN + "-" + USERNAME_MAX + " 个字符");
        }
        if (!USERNAME_PATTERN.matcher(name).matches()) {
            throw BusinessException.badRequest("用户名只能包含字母、数字以及 _ @ . - 符号");
        }
        return name;
    }

    private String validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw BusinessException.badRequest("密码不能为空");
        }
        int min = properties.getPasswordMinLength() > 0 ? properties.getPasswordMinLength() : 6;
        if (password.length() < min) {
            throw BusinessException.badRequest("密码至少 " + min + " 位");
        }
        if (password.length() > PASSWORD_MAX) {
            throw BusinessException.badRequest("密码最长 " + PASSWORD_MAX + " 位");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw BusinessException.badRequest("密码需同时包含字母和数字");
        }
        return password;
    }
}
