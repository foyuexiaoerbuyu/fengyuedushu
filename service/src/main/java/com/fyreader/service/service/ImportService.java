package com.fyreader.service.service;

import com.fyreader.service.common.BusinessException;
import com.fyreader.service.dto.ImportResult;
import com.fyreader.service.entity.Book;
import com.fyreader.service.entity.Chapter;
import com.fyreader.service.support.TxtImportParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * TXT 小说导入。
 *
 * <p>编码：按 BOM → 严格 UTF-8 试探 → 回落 GB18030 的优先级自动识别，落库统一为 UTF-8，
 * 避免 GBK/GB18030 文件被当作 UTF-8 读出乱码或发生二次转码。
 *
 * <p>章节：支持 第X章/节/回/卷/集/部/篇/幕、前言、序章/序言/序、楔子/引子、正文、
 * 尾声、后记/终章、番外、附录 以及 Chapter N；标题行独立成章，空章节与重复标题会被过滤。
 *
 * <p>重导：同名书籍存在时复用原 {@code book_id}，重建章节（chapter_no 从 1 连续重排），
 * 以尽量保留 App 侧按书籍与章节号记录的阅读进度。
 */
@Service
public class ImportService {

    private static final int PREVIEW_SIZE = 10;

    /** TXT 导入业务上限：需整体读入内存解析，故独立于 multipart 全局上限。 */
    private static final long MAX_TXT_SIZE = 64L * 1024L * 1024L;

    private final BookService bookService;
    private final ChapterService chapterService;

    public ImportService(BookService bookService, ChapterService chapterService) {
        this.bookService = bookService;
        this.chapterService = chapterService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importTxt(MultipartFile file, String bookName, String author,
                                  String category, String encoding) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件为空");
        }
        if (file.getSize() > MAX_TXT_SIZE) {
            throw BusinessException.badRequest("TXT 文件超过 64MB 上限");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(500, "读取文件失败: " + e.getMessage());
        }

        // 1. 编码识别 + 解码
        TxtImportParser.Decoded decoded = TxtImportParser.decode(bytes, encoding);

        // 2. 解析书名/作者与章节
        TxtImportParser.Parsed parsed = TxtImportParser.parse(decoded.text, bookName);
        List<TxtImportParser.ChapterDraft> drafts = parsed.chapters;
        if (drafts == null || drafts.isEmpty()) {
            throw BusinessException.badRequest("未解析到任何章节，请检查文件内容或编码");
        }

        String finalName = StringUtils.hasText(bookName) ? bookName.trim() : parsed.bookName;
        if (!StringUtils.hasText(finalName)) {
            finalName = stripExtension(file.getOriginalFilename());
        }
        if (!StringUtils.hasText(finalName)) {
            finalName = "未命名书籍";
        }
        String finalAuthor = StringUtils.hasText(author) ? author.trim()
                : (StringUtils.hasText(parsed.author) ? parsed.author : "佚名");
        String finalCategory = StringUtils.hasText(category) ? category.trim() : "其他";

        // 3. 组装章节实体
        List<Chapter> chapters = new ArrayList<>(drafts.size());
        int words = 0;
        int no = 1;
        for (TxtImportParser.ChapterDraft draft : drafts) {
            Chapter c = new Chapter();
            c.setChapterNo(no++);
            c.setTitle(draft.title);
            c.setContent(draft.content);
            int wc = ChapterService.calcWordCount(draft.content);
            c.setWordCount(wc);
            c.setVip(0);
            words += wc;
            chapters.add(c);
        }

        // 4. 同名书籍复用 book_id，避免重导产生新书导致阅读进度丢失
        Book existing = bookService.getByName(finalName);
        Book book;
        boolean reused = existing != null;
        if (reused) {
            book = existing;
            book.setAuthor(finalAuthor);
            book.setCategory(finalCategory);
            book.setStatus("已完结");
            book.setSourceName("本地导入");
            book.setSourceUrl("local-import");
            book.setRemark("TXT 导入，" + decoded.charsetName);
            book.setWordCount(formatWordCount(words));
            chapterService.deleteByBook(book.getId());
            bookService.update(book.getId(), book);
        } else {
            Book created = new Book();
            created.setName(finalName);
            created.setAuthor(finalAuthor);
            created.setCategory(finalCategory);
            created.setStatus("已完结");
            created.setSourceName("本地导入");
            created.setSourceUrl("local-import");
            created.setRemark("TXT 导入，" + decoded.charsetName);
            created.setWordCount(formatWordCount(words));
            book = bookService.create(created);
        }

        int count = chapterService.batchCreate(book.getId(), chapters);

        ImportResult result = new ImportResult();
        result.setBookId(book.getId());
        result.setBookName(book.getName());
        result.setAuthor(book.getAuthor());
        result.setChapterCount(count);
        result.setWordCount(words);
        result.setEncoding(decoded.charsetName);
        result.setEncodingDetectedBy(decoded.detectedBy);
        result.setReusedBook(reused);
        result.setDroppedEmptyChapters(parsed.droppedEmpty);
        result.setDroppedDuplicateChapters(parsed.droppedDuplicate);
        result.setChapterPreview(previewTitles(chapters));
        result.setMessage(reused
                ? "导入成功：同名书籍已存在，已复用书籍 ID 并重建章节"
                : "导入成功：已创建新书籍");
        return result;
    }

    /** 字数描述：t_book.word_count 为展示用字符串字段 */
    private static String formatWordCount(int words) {
        if (words >= 10000) {
            return String.format(java.util.Locale.ROOT, "%.1f万字", words / 10000.0);
        }
        return words + "字";
    }

    private List<String> previewTitles(List<Chapter> chapters) {
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < chapters.size() && i < PREVIEW_SIZE; i++) {
            titles.add(chapters.get(i).getTitle());
        }
        return titles;
    }

    private String stripExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
