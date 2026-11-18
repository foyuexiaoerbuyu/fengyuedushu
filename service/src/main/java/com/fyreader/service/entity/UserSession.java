package com.fyreader.service.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 登录会话，对应表 {@code t_user_session}。
 * <p>token 为服务端生成的高熵随机串，落库保存因此可吊销（登出即删除记录）。</p>
 */
@Data
public class UserSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;

    private String userId;

    /** 过期时间，默认签发后 7 天 */
    private Date expireTime;

    private Date createTime;

    public boolean isExpired() {
        return expireTime == null || expireTime.before(new Date());
    }
}
