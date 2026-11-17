package com.fyreader.service.entity;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 * 书籍实体。
 * <p>对应 App 侧 {@code entity.Book} 的"书籍本体字段"；客户端本地阅读状态
 * （阅读进度、书架分组、排序号、缓存 map、自定义变量等）不上传到服务端。</p>
 */
@Data
public class Book implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 书籍 ID，客户端可直接使用（缺省时服务端生成 UUID） */
    private String id;

    @NotBlank(message = "书名不能为空")
    @Size(max = 255, message = "书名过长")
    private String name;

    @Size(max = 128, message = "作者名过长")
    private String author;

    /** 封面地址 */
    private String coverUrl;

    /** 分类，对应 App 侧 Book.type */
    @Size(max = 64, message = "分类过长")
    private String category;

    /** 简介，对应 App 侧 Book.desc */
    private String intro;

    /** 连载状态：连载中 / 已完结 */
    @Size(max = 32, message = "状态过长")
    private String status;

    /** 字数描述，保留原始文本如 "12.3万字"，对应 App 侧 Book.wordCount */
    @Size(max = 32, message = "字数描述过长")
    private String wordCount;

    /** 最新章节 ID */
    private String latestChapterId;

    /** 最新章节标题 */
    private String latestChapterName;

    /** 章节总数 */
    private Integer chapterCount;

    /** 数据来源名称，对应 App 侧 Book.source */
    private String sourceName;

    /** 数据来源入口地址 */
    private String sourceUrl;

    /** 书籍详情页原始地址 */
    private String infoUrl;

    /** 备注 */
    private String remark;

    private Date createTime;

    private Date updateTime;
}
