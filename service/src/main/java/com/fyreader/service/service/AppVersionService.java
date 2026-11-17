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
}
