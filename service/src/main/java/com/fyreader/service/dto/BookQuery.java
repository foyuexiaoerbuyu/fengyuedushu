package com.fyreader.service.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 书籍查询条件 + 分页参数。
 * <p>sort 仅接受白名单值，由 Service 层转换为 SQL 片段，避免注入。</p>
 */
@Data
public class BookQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关键词：匹配书名 / 作者 */
    private String keyword;

    /** 精确分类过滤 */
    private String category;

    /** 连载状态过滤 */
    private String status;

    /** 来源名过滤 */
    private String sourceName;

    /** 排序：update_desc（默认）/ update_asc / name_asc / create_desc */
    private String sort;

    /** 页码，从 1 开始 */
    private Integer page = 1;

    /** 每页条数 */
    private Integer size = 20;

    public int offset(int defaultSize, int maxSize) {
        int s = sizeOrDefault(defaultSize, maxSize);
        int p = pageOrDefault();
        return (p - 1) * s;
    }

    public int sizeOrDefault(int defaultSize, int maxSize) {
        if (size == null || size <= 0) {
            return defaultSize;
        }
        return Math.min(size, maxSize);
    }

    public int pageOrDefault() {
        if (page == null || page <= 0) {
            return 1;
        }
        return page;
    }
}
