package com.fyreader.service.entity;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;

/**
 * App 版本实体，对应 t_app_version。
 * <p>替代原「语雀文档 + GitLab」更新源，客户端通过 {@code GET /api/v1/app/update} 拉取。</p>
 */
@Data
public class AppVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    /** 平台标识：android / ios */
    private String platform;

    @NotNull(message = "版本号不能为空")
    private Integer versionCode;

    /** 版本名，如 v2.7.1 */
    private String versionName;

    /** 是否强制更新：0 否，1 是 */
    private Integer forceUpdate;

    /** 低于该版本号强制更新 */
    private Integer minVersionCode;

    /** 安装包下载地址，为空时回落 /api/v1/app/apk */
    private String downloadUrl;

    /** 更新说明，多行用 \n 分隔 */
    private String updateContent;

    /** 备用域名 */
    private String domain;

    /** 插件配置地址（保留字段，当前客户端不启用插件体系） */
    private String pluginConfigUrl;

    /** 启动图地址 */
    private String splashImageUrl;

    /** 启动图 MD5 */
    private String splashImageMd5;

    /** 是否启用：0 否，1 是 */
    private Integer enabled;

    private Date createTime;

    private Date updateTime;
}
