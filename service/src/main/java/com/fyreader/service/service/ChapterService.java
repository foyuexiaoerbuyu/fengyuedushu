package com.fyreader.service.service;

import com.fyreader.service.common.BusinessException;
import com.fyreader.service.common.PageResult;
import com.fyreader.service.config.FyReaderProperties;
import com.fyreader.service.entity.Chapter;
import com.fyreader.service.mapper.BookMapper;
import com.fyreader.service.mapper.ChapterMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 章节业务逻辑。对应 App 侧 BookApi 的"目录 / 正文 / 上一章下一章"能力。
 */
@Service
public class ChapterService {

    private final ChapterMapper chapterMapper;
    private final BookMapper bookMapper;
    private final BookService bookService;
    private final FyReaderProperties properties;

    public ChapterService(ChapterMapper chapterMapper, BookMapper bookMapper,
                          BookService bookService, FyReaderProperties properties) {
        this.chapterMapper = chapterMapper;
        this.bookMapper = bookMapper;
        this.bookService = bookService;
        this.properties = properties;
    }

    /** 分页取章节（默认不带正文） */
    public PageResult<Chapter> pageByBook(String bookId, Integer page, Integer size, boolean withContent) {
        // 读接口统一走可见性校验，禁止按 bookId 直读他人私有书
        bookService.getReadable(bookId);
        int s = (size == null || size <= 0) ? properties.getDefaultPageSize()
                : Math.min(size, properties.getMaxPageSize());
        int p = (page == null || page <= 0) ? 1 : page;
        long total = chapterMapper.countByBookId(bookId);
        if (total == 0) {
            return PageResult.empty(p, s);
        }
        List<Chapter> records = chapterMapper.selectByBookId(bookId, withContent, (p - 1) * s, s);
        return new PageResult<>(records, total, p, s);
    }

    /** 全量目录（不含正文） */
    public List<Chapter> catalog(String bookId) {
        bookService.getReadable(bookId);
        long total = chapterMapper.countByBookId(bookId);
        if (total == 0) {
            return Collections.emptyList();
        }
        int limit = (int) Math.min(total, 100000L);
        List<Chapter> chapters = chapterMapper.selectByBookId(bookId, false, 0, limit);
        return chapters == null ? Collections.emptyList() : chapters;
    }

    public Chapter getContent(String bookId, String chapterId) {
        bookService.getReadable(bookId);
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null || !bookId.equals(chapter.getBookId())) {
            throw BusinessException.notFound("章节不存在: " + chapterId);
        }
        return chapter;
    }

    public Chapter getByNo(String bookId, Integer chapterNo) {
        bookService.getReadable(bookId);
        Chapter chapter = chapterMapper.selectByBookAndNo(bookId, chapterNo);
        if (chapter == null) {
            throw BusinessException.notFound("章节不存在: 第 " + chapterNo + " 章");
        }
        return chapter;
    }

    /** direction: next / prev */
    public Chapter neighbor(String bookId, Integer chapterNo, String direction) {
        bookService.getReadable(bookId);
        String dir = "prev".equalsIgnoreCase(direction) ? "prev" : "next";
        return chapterMapper.selectNeighbor(bookId, chapterNo, dir);
    }

    @Transactional(rollbackFor = Exception.class)
    public Chapter create(String bookId, Chapter chapter) {
        bookService.get(bookId);
        chapter.setBookId(bookId);
        if (!StringUtils.hasText(chapter.getId())) {
            chapter.setId("c_" + UUID.randomUUID().toString().replace("-", ""));
        } else if (chapterMapper.selectById(chapter.getId()) != null) {
            throw new BusinessException(409, "章节 ID 已存在: " + chapter.getId());
        }
        if (chapter.getChapterNo() == null || chapter.getChapterNo() <= 0) {
            chapter.setChapterNo(nextChapterNo(bookId));
        }
        chapter.setWordCount(calcWordCount(chapter.getContent()));
        chapterMapper.insert(chapter);
        bookMapper.refreshChapterStat(bookId);
        return chapterMapper.selectById(chapter.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public Chapter update(String chapterId, Chapter chapter) {
        Chapter exists = chapterMapper.selectById(chapterId);
        if (exists == null) {
            throw BusinessException.notFound("章节不存在: " + chapterId);
        }
        chapter.setId(chapterId);
        if (chapter.getContent() != null) {
            chapter.setWordCount(calcWordCount(chapter.getContent()));
        }
        chapterMapper.update(chapter);
        bookMapper.refreshChapterStat(exists.getBookId());
        return chapterMapper.selectById(chapterId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String chapterId) {
        Chapter exists = chapterMapper.selectById(chapterId);
        if (exists == null) {
            throw BusinessException.notFound("章节不存在: " + chapterId);
        }
        chapterMapper.deleteById(chapterId);
        bookMapper.refreshChapterStat(exists.getBookId());
    }

    /** 清空某本书的全部章节（TXT 重导时先重建章节） */
    @Transactional(rollbackFor = Exception.class)
    public int deleteByBook(String bookId) {
        if (!StringUtils.hasText(bookId)) {
            return 0;
        }
        int deleted = chapterMapper.deleteByBookId(bookId);
        bookMapper.refreshChapterStat(bookId);
        return deleted;
    }

    /** 批量新增（TXT 导入用） */
    @Transactional(rollbackFor = Exception.class)
    public int batchCreate(String bookId, List<Chapter> chapters) {
        if (chapters == null || chapters.isEmpty()) {
            return 0;
        }
        int baseNo = nextChapterNo(bookId) - 1;
        List<Chapter> toInsert = new ArrayList<>(chapters.size());
        for (int i = 0; i < chapters.size(); i++) {
            Chapter c = chapters.get(i);
            c.setBookId(bookId);
            if (!StringUtils.hasText(c.getId())) {
                c.setId("c_" + UUID.randomUUID().toString().replace("-", ""));
            }
            if (c.getChapterNo() == null || c.getChapterNo() <= 0) {
                c.setChapterNo(baseNo + i + 1);
            }
            if (c.getWordCount() == null) {
                c.setWordCount(calcWordCount(c.getContent()));
            }
            toInsert.add(c);
        }
        for (Chapter c : toInsert) {
            chapterMapper.insert(c);
        }
        bookMapper.refreshChapterStat(bookId);
        return toInsert.size();
    }

    public int nextChapterNo(String bookId) {
        Integer maxNo = chapterMapper.selectMaxChapterNo(bookId);
        return maxNo == null ? 1 : maxNo + 1;
    }

    /** 正文字数：去掉所有空白字符后的长度 */
    public static int calcWordCount(String content) {
        if (content == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < content.length(); i++) {
            if (!Character.isWhitespace(content.charAt(i))) {
                count++;
            }
        }
        return count;
    }
}
