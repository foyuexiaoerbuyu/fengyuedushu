package com.fyreader.service.entity;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Date;

/**
 * 书源实体。
 * <p>相对 App 侧 {@code entity.rule.BookSource} 已剥离全部爬虫解析规则
 * （searchRule / infoRule / tocRule / contentRule / loginUrl），仅保留元数据。</p>
 */
@Data
public class BookSource implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    @NotBlank(message = "书源名称不能为空")
    private String sourceName;

    /** 书源入口地址 */
    private String sourceUrl;

    /** 书源类型：API / LOCAL / IMPORT */
    private String sourceType;

    /** 分组名 */
    private String groupName;

    /** 是否启用：0 否，1 是 */
    private Integer enabled;

    private String remark;

    private Date createTime;

    private Date updateTime;
}
