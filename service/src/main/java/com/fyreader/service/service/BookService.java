package com.fyreader.service.service;

import com.fyreader.service.common.BusinessException;
import com.fyreader.service.common.PageResult;
import com.fyreader.service.config.FyReaderProperties;
import com.fyreader.service.dto.BookQuery;
import com.fyreader.service.entity.Book;
import com.fyreader.service.mapper.BookMapper;
import com.fyreader.service.mapper.ChapterMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 书籍业务逻辑。对应 App 侧 BookApi 的"书籍详情 / 搜索 / 发现"等能力，现改为读库。
 */
@Service
public class BookService {

    private final BookMapper bookMapper;
    private final ChapterMapper chapterMapper;
    private final FyReaderProperties properties;

    public BookService(BookMapper bookMapper, ChapterMapper chapterMapper, FyReaderProperties properties) {
        this.bookMapper = bookMapper;
        this.chapterMapper = chapterMapper;
        this.properties = properties;
    }

    public PageResult<Book> page(BookQuery query) {
        BookQuery q = query == null ? new BookQuery() : query;
        int size = q.sizeOrDefault(properties.getDefaultPageSize(), properties.getMaxPageSize());
        int page = q.pageOrDefault();
        long total = bookMapper.countByQuery(q);
        if (total == 0) {
            return PageResult.empty(page, size);
        }
        List<Book> records = bookMapper.selectByQuery(q, resolveOrderBy(q.getSort()), (page - 1) * size, size);
        return new PageResult<>(records, total, page, size);
    }

    public Book get(String id) {
        if (!StringUtils.hasText(id)) {
            throw BusinessException.badRequest("书籍 ID 不能为空");
        }
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw BusinessException.notFound("书籍不存在: " + id);
        }
        return book;
    }

    public Book getOrNull(String id) {
        return bookMapper.selectById(id);
    }

    public List<Book> listAll() {
        List<Book> books = bookMapper.selectAll();
        return books == null ? Collections.emptyList() : books;
    }

    @Transactional(rollbackFor = Exception.class)
    public Book create(Book book) {
        if (book == null || !StringUtils.hasText(book.getName())) {
            throw BusinessException.badRequest("书名不能为空");
        }
        if (!StringUtils.hasText(book.getId())) {
            book.setId("b_" + UUID.randomUUID().toString().replace("-", ""));
        } else if (bookMapper.selectById(book.getId()) != null) {
            throw new BusinessException(409, "书籍 ID 已存在: " + book.getId());
        }
        if (book.getChapterCount() == null) {
            book.setChapterCount(0);
        }
        bookMapper.insert(book);
        return bookMapper.selectById(book.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public Book update(String id, Book book) {
        Book exists = get(id);
        book.setId(exists.getId());
        bookMapper.update(book);
        return bookMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        get(id);
        chapterMapper.deleteByBookId(id);
        bookMapper.deleteById(id);
    }

    /** 依据章节表回写章节总数与最新章节 */
    @Transactional(rollbackFor = Exception.class)
    public Book refreshStat(String bookId) {
        get(bookId);
        bookMapper.refreshChapterStat(bookId);
        return bookMapper.selectById(bookId);
    }

    /** 排序白名单，避免 SQL 注入 */
    private String resolveOrderBy(String sort) {
        if (!StringUtils.hasText(sort)) {
            return "update_time DESC";
        }
        switch (sort) {
            case "update_asc":
                return "update_time ASC";
            case "name_asc":
                return "name ASC";
            case "name_desc":
                return "name DESC";
            case "create_desc":
                return "create_time DESC";
            case "update_desc":
            default:
                return "update_time DESC";
        }
    }
}
