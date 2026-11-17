package com.fyreader.service.service;

import com.fyreader.service.common.BusinessException;
import com.fyreader.service.dto.ImportResult;
import com.fyreader.service.entity.Book;
import com.fyreader.service.entity.Chapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TXT 小说导入：按"第X章 / Chapter N"标题切分章节，落库为书籍 + 章节。
 */
@Service
public class ImportService {

    /** 章节标题匹配：第一章 xxx / 第12回 / Chapter 3 ... */
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "^\\s*(第[0-9零一二三四五六七八九十百千两]{1,10}[章节回卷集篇][^\\r\\n]{0,60}"
                    + "|Chapter\\s+\\d{1,5}[^\\r\\n]{0,60}"
                    + "|CHAPTER\\s+\\d{1,5}[^\\r\\n]{0,60})\\s*$");

    private static final int PREVIEW_SIZE = 10;

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
        String charsetName = StringUtils.hasText(encoding) ? encoding.trim() : "UTF-8";
        Charset charset;
        try {
            charset = Charset.forName(charsetName);
        } catch (Exception e) {
            throw BusinessException.badRequest("不支持的编码: " + charsetName);
        }

        List<String> lines = readLines(file, charset);
        // 未显式指定书名时，若首行为短标题（非章节标题），视为书名并从正文中剔除
        if (!StringUtils.hasText(bookName)) {
            List<String> copy = new ArrayList<>(lines);
            String head = null;
            int headIndex = -1;
            for (int i = 0; i < copy.size(); i++) {
                String t = copy.get(i).trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (t.length() <= 40 && !CHAPTER_PATTERN.matcher(t).matches()) {
                    head = t;
                    headIndex = i;
                }
                break;
            }
            if (head != null) {
                for (int i = headIndex; i >= 0; i--) {
                    copy.remove(i);
                }
                while (!copy.isEmpty() && copy.get(0).trim().isEmpty()) {
                    copy.remove(0);
                }
                lines = copy;
                bookName = head;
            }
        }
        List<Chapter> chapters = splitChapters(lines);
        if (chapters.isEmpty()) {
            throw BusinessException.badRequest("未解析到任何章节，请检查文件内容或更换编码");
        }

        String finalName = StringUtils.hasText(bookName) ? bookName.trim() : stripExtension(file.getOriginalFilename());
        if (!StringUtils.hasText(finalName)) {
            finalName = "未命名书籍";
        }

        Book book = new Book();
        book.setName(finalName);
        book.setAuthor(StringUtils.hasText(author) ? author.trim() : "佚名");
        book.setCategory(StringUtils.hasText(category) ? category.trim() : "其他");
        book.setStatus("已完结");
        book.setSourceName("本地导入");
        book.setSourceUrl("local-import");
        book.setRemark("TXT 导入，" + charsetName);
        Book created = bookService.create(book);

        int count = chapterService.batchCreate(created.getId(), chapters);

        ImportResult result = new ImportResult();
        result.setBookId(created.getId());
        result.setBookName(created.getName());
        result.setAuthor(created.getAuthor());
        result.setChapterCount(count);
        result.setEncoding(charsetName);
        int words = 0;
        for (Chapter c : chapters) {
            words += c.getWordCount() == null ? 0 : c.getWordCount();
        }
        result.setWordCount(words);
        result.setChapterPreview(previewTitles(chapters));
        result.setMessage("导入成功");
        return result;
    }

    private List<String> previewTitles(List<Chapter> chapters) {
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < chapters.size() && i < PREVIEW_SIZE; i++) {
            titles.add(chapters.get(i).getTitle());
        }
        return titles;
    }

    private List<String> readLines(MultipartFile file, Charset charset) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(file.getBytes()), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line.replace("\uFEFF", ""));
            }
        } catch (IOException e) {
            throw new BusinessException(500, "读取文件失败: " + e.getMessage());
        }
        return lines;
    }

    /** 逐行扫描切分章节，标题前的正文作为"前言"章节 */
    private List<Chapter> splitChapters(List<String> lines) {
        List<Chapter> chapters = new ArrayList<>();
        String title = null;
        StringBuilder content = new StringBuilder();

        for (String line : lines) {
            Matcher matcher = CHAPTER_PATTERN.matcher(line);
            if (matcher.matches()) {
                flush(chapters, title, content);
                title = matcher.group().trim();
                content = new StringBuilder();
            } else {
                content.append(line).append('\n');
            }
        }
        flush(chapters, title, content);

        int no = 1;
        for (Chapter c : chapters) {
            c.setChapterNo(no++);
        }
        return chapters;
    }

    private void flush(List<Chapter> chapters, String title, StringBuilder content) {
        String text = content.toString().trim();
        if (title == null) {
            if (text.isEmpty()) {
                return;
            }
            // 章节标题前的正文
            Chapter pre = new Chapter();
            pre.setTitle("前言");
            pre.setContent(text);
            pre.setWordCount(ChapterService.calcWordCount(text));
            pre.setVip(0);
            chapters.add(pre);
            return;
        }
        Chapter chapter = new Chapter();
        chapter.setTitle(title);
        chapter.setContent(text);
        chapter.setWordCount(ChapterService.calcWordCount(text));
        chapter.setVip(0);
        chapters.add(chapter);
    }

    private String stripExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
