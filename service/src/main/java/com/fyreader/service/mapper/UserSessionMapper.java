package com.fyreader.service.mapper;

import com.fyreader.service.entity.UserSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

@Mapper
public interface UserSessionMapper {

    int insert(UserSession session);

    UserSession selectByToken(@Param("token") String token);

    int deleteByToken(@Param("token") String token);

    /** 会话表可吊销：某个用户的全部会话一并失效 */
    int deleteByUserId(@Param("userId") String userId);

    /** 吊销某用户除指定 token 外的全部会话（改密码时保留当前设备） */
    int deleteByUserIdExcept(@Param("userId") String userId, @Param("keepToken") String keepToken);

    /** 清理过期会话，登录时顺带执行，避免表无限膨胀 */
    int deleteExpired(@Param("now") Date now);
}
