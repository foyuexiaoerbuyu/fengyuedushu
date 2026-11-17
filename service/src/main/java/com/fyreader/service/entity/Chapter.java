package com.fyreader.service.entity;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;

/**
 * 章节实体，对应 App 侧 {@code entity.Chapter}。
 */
@Data
public class Chapter implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    @NotNull(message = "书籍 ID 不能为空")
    private String bookId;

    /** 章节序号，从 1 开始 */
    private Integer chapterNo;

    /** 章节标题 */
    private String title;

    /** 章节正文 */
    private String content;

    /** 正文字数 */
    private Integer wordCount;

    /** 是否 VIP 章节：0 否，1 是 */
    private Integer vip;

    private Date createTime;

    private Date updateTime;
}
