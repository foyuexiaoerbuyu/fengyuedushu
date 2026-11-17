package com.fyreader.service.mapper;

import com.fyreader.service.entity.AppVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppVersionMapper {

    /** 取指定平台下 version_code 最大的启用版本；无启用版本时允许返回 null */
    AppVersion selectLatest(@Param("platform") String platform);

    AppVersion selectById(@Param("id") String id);

    int insert(AppVersion version);

    int update(AppVersion version);
}
