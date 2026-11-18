package com.fyreader.service.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户实体，对应表 {@code t_user}。
 * <p>密码只保存 BCrypt 摘要，任何接口都不得下发 {@link #passwordHash}。</p>
 */
@Data
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户状态：启用 */
    public static final int STATUS_ENABLED = 1;

    /** 用户状态：停用 */
    public static final int STATUS_DISABLED = 0;

    private String id;

    private String username;

    /** BCrypt 摘要 */
    private String passwordHash;

    private String nickname;

    /** 1 启用 / 0 停用 */
    private Integer status;

    private Date createTime;

    private Date updateTime;

    public boolean isEnabled() {
        return status == null || status == STATUS_ENABLED;
    }
}
