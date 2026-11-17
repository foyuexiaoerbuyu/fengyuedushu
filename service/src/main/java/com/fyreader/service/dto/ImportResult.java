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

    /** 编码识别依据（BOM / 严格 UTF-8 探测 / 回落 GB18030 / 用户指定） */
    private String encodingDetectedBy;

    /** 是否复用了已存在的同名书籍 ID */
    private boolean reusedBook;

    /** 被过滤的空正文章节数 */
    private int droppedEmptyChapters;

    /** 被过滤的重复章节数 */
    private int droppedDuplicateChapters;

    /** 前若干个章节标题，便于前端确认解析是否正确 */
    private List<String> chapterPreview = new ArrayList<>();

    /** 未识别到章节标题时的提示信息 */
    private String message;
}
