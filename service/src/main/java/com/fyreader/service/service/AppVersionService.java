package com.fyreader.service.service;

import com.fyreader.service.common.BusinessException;
import com.fyreader.service.entity.AppVersion;
import com.fyreader.service.mapper.AppVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * App 版本管理：客户端更新检查的唯一数据来源。
 */
@Service
public class AppVersionService {

    private static final String DEFAULT_PLATFORM = "android";

    private final AppVersionMapper appVersionMapper;

    public AppVersionService(AppVersionMapper appVersionMapper) {
        this.appVersionMapper = appVersionMapper;
    }

    /** 取指定平台最新启用版本，无数据返回 null */
    public AppVersion latest(String platform) {
        String target = StringUtils.hasText(platform) ? platform.trim() : DEFAULT_PLATFORM;
        return appVersionMapper.selectLatest(target);
    }

    /** 保存版本信息：id 命中则更新，否则插入；返回落库后的最新记录 */
    @Transactional(rollbackFor = Exception.class)
    public AppVersion save(AppVersion version) {
        if (version == null || version.getVersionCode() == null) {
            throw BusinessException.badRequest("版本号不能为空");
        }
        if (version.getDownloadUrl() != null) {
            version.setDownloadUrl(normalizeDownloadUrl(version.getDownloadUrl()));
        }
        if (!StringUtils.hasText(version.getPlatform())) {
            version.setPlatform(DEFAULT_PLATFORM);
        }
        if (version.getForceUpdate() == null) {
            version.setForceUpdate(0);
        }
        if (version.getMinVersionCode() == null) {
            version.setMinVersionCode(0);
        }
        if (version.getEnabled() == null) {
            version.setEnabled(1);
        }
        AppVersion exists = StringUtils.hasText(version.getId())
                ? appVersionMapper.selectById(version.getId())
                : null;
        if (exists == null) {
            if (!StringUtils.hasText(version.getId())) {
                version.setId("av_" + UUID.randomUUID().toString().replace("-", ""));
            }
            appVersionMapper.insert(version);
            return appVersionMapper.selectById(version.getId());
        }
        appVersionMapper.update(version);
        return appVersionMapper.selectById(version.getId());
    }

    /**
     * 下载地址白名单：只允许留空（回落站内下载接口）或以 "/" 开头的站内路径。
     * <p>拒绝 http(s)://、协议相对地址 "//host"、反斜杠与 ".." 穿越，
     * 避免通过改版本配置把客户端更新指向任意外部 APK。</p>
     */
    private String normalizeDownloadUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String value = url.trim();
        if (value.startsWith("//")) {
            throw BusinessException.badRequest("下载地址只允许站内路径（以 / 开头），不能使用协议相对地址");
        }
        if (!value.startsWith("/")) {
            throw BusinessException.badRequest("下载地址只允许站内路径（以 / 开头）或留空");
        }
        if (value.contains("\\") || value.contains("..")) {
            throw BusinessException.badRequest("下载地址不允许包含反斜杠或 .. 路径穿越");
        }
        return value;
    }
}
