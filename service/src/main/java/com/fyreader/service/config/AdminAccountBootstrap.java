package com.fyreader.service.config;

import com.fyreader.service.entity.User;
import com.fyreader.service.mapper.UserMapper;
import com.fyreader.service.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 首次启动引导：库中不存在任何 ADMIN 账号时，自动创建管理员并生成随机密码。
 *
 * <p>设计取舍（B 方案）：</p>
 * <ul>
 *   <li>密码不写死在代码或配置里，改为启动时随机生成，只出现在启动日志与 data/admin-password.txt 中；</li>
 *   <li>账号已存在则完全跳过，不会覆盖管理员自己改过的密码；</li>
 *   <li>登录后应立即在后台「修改密码」中改掉初始密码，并删除 data/admin-password.txt。</li>
 * </ul>
 */
@Component
public class AdminAccountBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountBootstrap.class);

    private static final int PASSWORD_LENGTH = 14;
    private static final int NICKNAME_MAX = 32;

    private final UserMapper userMapper;
    private final AuthService authService;
    private final FyReaderProperties properties;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminAccountBootstrap(UserMapper userMapper, AuthService authService, FyReaderProperties properties) {
        this.userMapper = userMapper;
        this.authService = authService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.countByRole(User.ROLE_ADMIN) > 0) {
            log.info("管理员账号已存在，跳过引导创建");
            return;
        }
        String username = StringUtils.hasText(properties.getAdminUsername())
                ? properties.getAdminUsername().trim() : "admin";
        User existing = userMapper.selectByUsername(username);
        if (existing != null) {
            // 同名账号已存在（历史普通用户），不擅自提权，交人工处理
            log.warn("用户名 {} 已被占用且不是管理员，未自动创建管理员账号；请改名 FYREADER_ADMIN_USERNAME 或手工在库中调整", username);
            return;
        }
        String password = authService.generatePassword(PASSWORD_LENGTH);
        User admin = new User();
        admin.setId("u_" + UUID.randomUUID().toString().replace("-", ""));
        admin.setUsername(username);
        admin.setPasswordHash(encoder.encode(password));
        String nickname = "管理员";
        admin.setNickname(nickname.length() > NICKNAME_MAX ? nickname.substring(0, NICKNAME_MAX) : nickname);
        admin.setStatus(User.STATUS_ENABLED);
        admin.setRole(User.ROLE_ADMIN);
        userMapper.insert(admin);

        String fileHint = persistPassword(username, password);
        log.warn("\n============================================================\n"
                + "  管理员账号已创建（仅本次显示，请立即记录并登录后台修改密码）\n"
                + "  用户名：{}\n"
                + "  密  码：{}\n"
                + "  已保存到：{}\n"
                + "  登录地址：/index.html\n"
                + "  修改密码后请删除该文件\n"
                + "============================================================",
                username, password, fileHint);
    }

    /** 把初始密码追加写入 data/admin-password.txt，便于日志被清后仍可找回 */
    private String persistPassword(String username, String password) {
        Path dir = Paths.get("./data");
        Path file = dir.resolve("admin-password.txt");
        String line = String.format("[%s] username=%s password=%s%n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), username, password);
        try {
            Files.createDirectories(dir);
            Files.write(file, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return file.toAbsolutePath().normalize().toString();
        } catch (IOException e) {
            log.warn("初始密码写入 {} 失败：{}（请以本条日志中的密码为准）", file.toAbsolutePath(), e.getMessage());
            return "(写入失败，仅见日志)";
        }
    }
}
