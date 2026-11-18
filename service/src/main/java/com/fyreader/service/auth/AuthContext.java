package com.fyreader.service.auth;

/**
 * 单次请求的身份上下文，由 {@code TokenInterceptor} 在 preHandle 中写入、afterCompletion 中清理。
 *
 * <p>三级身份：</p>
 * <ul>
 *   <li>{@link Role#ANONYMOUS}：未携带任何有效凭证，仅可读公开书（visibility=PUBLIC）</li>
 *   <li>{@link Role#USER}：携带有效登录 token，可读公开书 + 自己名下的私有书</li>
 *   <li>{@link Role#ADMIN}：携带管理口令或共享访问口令，视为服务部署者本人，可读全部书籍</li>
 * </ul>
 */
public final class AuthContext {

    public enum Role {
        ANONYMOUS,
        USER,
        ADMIN
    }

    private static final AuthContext ANONYMOUS = new AuthContext(Role.ANONYMOUS, null, null, null);
    private static final AuthContext ADMIN = new AuthContext(Role.ADMIN, null, "admin", null);

    private static final ThreadLocal<AuthContext> HOLDER = new ThreadLocal<AuthContext>() {
        @Override
        protected AuthContext initialValue() {
            return ANONYMOUS;
        }
    };

    private final Role role;
    private final String userId;
    private final String username;
    /** 本次请求携带并通过校验的凭证，登出时需要据此吊销会话 */
    private final String token;

    private AuthContext(Role role, String userId, String username, String token) {
        this.role = role;
        this.userId = userId;
        this.username = username;
        this.token = token;
    }

    public static AuthContext anonymous() {
        return ANONYMOUS;
    }

    public static AuthContext admin(String token) {
        return new AuthContext(Role.ADMIN, null, "admin", token);
    }

    public static AuthContext ofUser(String userId, String username, String token) {
        return new AuthContext(Role.USER, userId, username, token);
    }

    public static void set(AuthContext context) {
        HOLDER.set(context == null ? ANONYMOUS : context);
    }

    public static AuthContext current() {
        AuthContext context = HOLDER.get();
        return context == null ? ANONYMOUS : context;
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 当前登录用户 ID；匿名或管理口令身份返回 null */
    public static String currentUserId() {
        return current().getUserId();
    }

    /** 本次请求携带的凭证（已通过校验），无则返回 null */
    public static String currentToken() {
        return current().getToken();
    }

    public String getToken() {
        return token;
    }

    public Role getRole() {
        return role;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public boolean isAnonymous() {
        return role == Role.ANONYMOUS;
    }

    public boolean isUser() {
        return role == Role.USER;
    }

    /** 是否具备"读取全部书籍"的部署者身份（管理口令 / 共享口令） */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
