package com.fyreader.service.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一分页返回体。
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> records = new ArrayList<>();
    /** 总记录数 */
    private long total;
    /** 当前页码，从 1 开始 */
    private int page;
    /** 每页条数 */
    private int size;
    /** 总页数 */
    private int pages;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, int page, int size) {
        this.records = records == null ? new ArrayList<>() : records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = size <= 0 ? 0 : (int) ((total + size - 1) / size);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(new ArrayList<>(), 0L, page, size);
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }
}
