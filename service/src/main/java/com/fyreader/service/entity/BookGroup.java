package com.fyreader.service.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 书籍分组实体，对应 App 侧 {@code entity.BookGroup}（客户端仍以本地分组为主，服务端仅下发默认分组）。
 */
@Data
public class BookGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private String groupName;

    private Integer sortNo;

    private String remark;

    private Date createTime;
}
