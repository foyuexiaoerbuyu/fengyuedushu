package com.fyreader.service.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * TXT 导入结果。
 */
@Data
public class ImportResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String bookId;

    private String bookName;

    private String author;

    /** 解析出的章节数 */
    private int chapterCount;

    /** 正文字数合计 */
    private int wordCount;

    /** 使用的字符编码 */
    private String encoding;

    /** 前若干个章节标题，便于前端确认解析是否正确 */
    private List<String> chapterPreview = new ArrayList<>();

    /** 未识别到章节标题时的提示信息 */
    private String message;
}
