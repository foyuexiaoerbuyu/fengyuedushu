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

    /** 是否开启 /api/v1/** 的 token 校验 */
    private boolean authEnabled = false;

    /** 校验用 token，请求头 X-FY-Token */
    private String token = "";

    /** 默认分页大小 */
    private int defaultPageSize = 20;

    /** 单页最大条数 */
    private int maxPageSize = 200;

    /** APK 存放目录，/api/v1/app/apk 从这里取最新的安装包 */
    private String apkDir = "./data/apk";
}
