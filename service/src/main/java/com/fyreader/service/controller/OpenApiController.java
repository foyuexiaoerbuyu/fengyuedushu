package com.fyreader.service.controller;

import com.fyreader.service.common.PageResult;
import com.fyreader.service.dto.BookQuery;
import com.fyreader.service.entity.Book;
import com.fyreader.service.entity.BookGroup;
import com.fyreader.service.entity.BookSource;
import com.fyreader.service.entity.Chapter;
import com.fyreader.service.mapper.BookGroupMapper;
import com.fyreader.service.service.BookService;
import com.fyreader.service.service.BookSourceService;
import com.fyreader.service.service.ChapterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端接口（App 侧调用），替代原爬虫解析链路。
 * <p>所有返回体由 {@code ApiResponseAdvice} 统一包装为 {@code Result}。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class OpenApiController {

    private final BookService bookService;
    private final ChapterService chapterService;
    private final BookSourceService bookSourceService;
    private final BookGroupMapper bookGroupMapper;

    public OpenApiController(BookService bookService, ChapterService chapterService,
                             BookSourceService bookSourceService, BookGroupMapper bookGroupMapper) {
        this.bookService = bookService;
        this.chapterService = chapterService;
        this.bookSourceService = bookSourceService;
        this.bookGroupMapper = bookGroupMapper;
    }

    /** 健康检查 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "fyreader-service");
        data.put("time", System.currentTimeMillis());
        return data;
    }

    /**
     * 书籍列表 / 搜索 / 发现 三合一。
     * keyword 命中书名或作者即视为搜索，不带参数即按更新时间倒序的推荐列表。
     */
    @GetMapping("/books")
    public PageResult<Book> books(BookQuery query) {
        return bookService.page(query);
    }

    /** 分类聚合列表，供 App 发现页生成分类 Tab */
    @GetMapping("/categories")
    public Map<String, Object> categories() {
        return bookService.categories();
    }

    /** 书籍详情 */
    @GetMapping("/books/{id}")
    public Book book(@PathVariable("id") String id) {
        return bookService.get(id);
    }

    /** 章节目录（分页，默认不含正文） */
    @GetMapping("/books/{id}/chapters")
    public PageResult<Chapter> chapters(@PathVariable("id") String id,
                                        @RequestParam(value = "page", required = false) Integer page,
                                        @RequestParam(value = "size", required = false) Integer size,
                                        @RequestParam(value = "withContent", defaultValue = "false") boolean withContent) {
        return chapterService.pageByBook(id, page, size, withContent);
    }

    /** 章节目录（全量，不含正文），对应 App 侧 getBookChapters */
    @GetMapping("/books/{id}/catalog")
    public List<Chapter> catalog(@PathVariable("id") String id) {
        return chapterService.catalog(id);
    }

    /** 章节正文，对应 App 侧 getChapterContent */
    @GetMapping("/books/{id}/chapters/{chapterId}")
    public Chapter chapterContent(@PathVariable("id") String id,
                                  @PathVariable("chapterId") String chapterId) {
        return chapterService.getContent(id, chapterId);
    }

    /** 按章节序号取正文 */
    @GetMapping("/books/{id}/chapters/no/{chapterNo}")
    public Chapter chapterByNo(@PathVariable("id") String id,
                               @PathVariable("chapterNo") Integer chapterNo) {
        return chapterService.getByNo(id, chapterNo);
    }

    /** 上一章 / 下一章，direction=next|prev */
    @GetMapping("/books/{id}/chapters/{chapterNo}/neighbor")
    public Chapter neighbor(@PathVariable("id") String id,
                            @PathVariable("chapterNo") Integer chapterNo,
                            @RequestParam(value = "direction", defaultValue = "next") String direction) {
        return chapterService.neighbor(id, chapterNo, direction);
    }

    /** 可用书源列表 */
    @GetMapping("/sources")
    public List<BookSource> sources() {
        return bookSourceService.list(true);
    }

    /** 内置分组 */
    @GetMapping("/groups")
    public List<BookGroup> groups() {
        return bookGroupMapper.selectAll();
    }
}
