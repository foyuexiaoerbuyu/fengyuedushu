package com.fyreader.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 服务自定义配置项，对应 application.yml 中 fyreader 前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fyreader")
public class FyReaderProperties {

    /** 是否开启 /api/v1/** 的身份校验（/api/admin/** 不受此开关影响，始终校验管理口令） */
    private boolean authEnabled = false;

    /**
     * 共享访问口令，请求头 X-FY-Token；对应环境变量 FYREADER_TOKEN。
     * <p>携带该口令视为服务部署者本人，可读取全部书籍（含他人私有书），用于兼容旧版单机客户端。
     * 为空时该通道关闭，只能通过登录 token 访问。</p>
     */
    private String token = "";

    /** 是否允许自助注册；对应环境变量 FYREADER_ALLOW_REGISTER，默认关闭，账号由管理员在后台开通 */
    private boolean allowRegister = false;

    /** 引导创建的管理员用户名；对应环境变量 FYREADER_ADMIN_USERNAME，默认 admin */
    private String adminUsername = "admin";

    /** 登录会话有效期（天）；对应环境变量 FYREADER_SESSION_DAYS */
    private int sessionDays = 7;

    /** 密码最小长度；对应环境变量 FYREADER_PASSWORD_MIN_LENGTH */
    private int passwordMinLength = 6;

    /** 管理端 token，请求头 X-Admin-Token；对应环境变量 FYREADER_ADMIN_TOKEN，为空时 /api/admin/** 一律拒绝 */
    private String adminToken = "";

    /** 默认分页大小 */
    private int defaultPageSize = 20;

    /** 单页最大条数 */
    private int maxPageSize = 200;

    /** APK 存放目录，/api/v1/app/apk 从这里取最新的安装包 */
    private String apkDir = "./data/apk";
}
