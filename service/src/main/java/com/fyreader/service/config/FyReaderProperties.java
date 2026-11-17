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

    /** 是否开启 /api/v1/** 的 token 校验（/api/admin/** 不受此开关影响，始终校验管理口令） */
    private boolean authEnabled = false;

    /** 客户端 token，请求头 X-FY-Token；对应环境变量 FYREADER_TOKEN */
    private String token = "";

    /** 管理端 token，请求头 X-Admin-Token；对应环境变量 FYREADER_ADMIN_TOKEN，为空时 /api/admin/** 一律拒绝 */
    private String adminToken = "";

    /** 默认分页大小 */
    private int defaultPageSize = 20;

    /** 单页最大条数 */
    private int maxPageSize = 200;

    /** APK 存放目录，/api/v1/app/apk 从这里取最新的安装包 */
    private String apkDir = "./data/apk";
}
