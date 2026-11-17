package com.fyreader.service.controller;

import com.fyreader.service.common.PageResult;
import com.fyreader.service.dto.BookQuery;
import com.fyreader.service.dto.ImportResult;
import com.fyreader.service.entity.AppVersion;
import com.fyreader.service.entity.Book;
import com.fyreader.service.entity.BookSource;
import com.fyreader.service.entity.Chapter;
import com.fyreader.service.mapper.BookMapper;
import com.fyreader.service.mapper.ChapterMapper;
import com.fyreader.service.service.ApkStorageService;
import com.fyreader.service.service.AppVersionService;
import com.fyreader.service.service.BookService;
import com.fyreader.service.service.BookSourceService;
import com.fyreader.service.service.ChapterService;
import com.fyreader.service.service.ImportService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端接口，供静态页面 /index.html 使用。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final BookService bookService;
    private final ChapterService chapterService;
    private final BookSourceService bookSourceService;
    private final ImportService importService;
    private final BookMapper bookMapper;
    private final ChapterMapper chapterMapper;
    private final AppVersionService appVersionService;
    private final ApkStorageService apkStorageService;

    public AdminApiController(BookService bookService, ChapterService chapterService,
                              BookSourceService bookSourceService, ImportService importService,
                              BookMapper bookMapper, ChapterMapper chapterMapper,
                              AppVersionService appVersionService,
                              ApkStorageService apkStorageService) {
        this.bookService = bookService;
        this.chapterService = chapterService;
        this.bookSourceService = bookSourceService;
        this.importService = importService;
        this.bookMapper = bookMapper;
        this.chapterMapper = chapterMapper;
        this.appVersionService = appVersionService;
        this.apkStorageService = apkStorageService;
    }

    /** 分类聚合，供后台分类筛选下拉使用 */
    @GetMapping("/categories")
    public Map<String, Object> categories() {
        return bookService.categories();
    }

    /** 概览统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bookCount", bookMapper.countByQuery(new BookQuery()));
        data.put("chapterCount", chapterMapper.countAll());
        data.put("sourceCount", bookSourceService.list(false).size());
        return data;
    }

    // ---------------- 书籍 ----------------

    @GetMapping("/books")
    public PageResult<Book> listBooks(BookQuery query) {
        return bookService.page(query);
    }

    @GetMapping("/books/{id}")
    public Book getBook(@PathVariable("id") String id) {
        return bookService.get(id);
    }

    @PostMapping("/books")
    public Book createBook(@Valid @RequestBody Book book) {
        return bookService.create(book);
    }

    @PutMapping("/books/{id}")
    public Book updateBook(@PathVariable("id") String id, @RequestBody Book book) {
        return bookService.update(id, book);
    }

    @DeleteMapping("/books/{id}")
    public Map<String, Object> deleteBook(@PathVariable("id") String id) {
        bookService.delete(id);
        return ok("已删除书籍及其全部章节");
    }

    /** 按章节表重算章节总数与最新章节 */
    @PostMapping("/books/{id}/refresh")
    public Book refreshBook(@PathVariable("id") String id) {
        return bookService.refreshStat(id);
    }

    // ---------------- 章节 ----------------

    @GetMapping("/books/{id}/chapters")
    public PageResult<Chapter> listChapters(@PathVariable("id") String id,
                                            @RequestParam(value = "page", required = false) Integer page,
                                            @RequestParam(value = "size", required = false) Integer size,
                                            @RequestParam(value = "withContent", defaultValue = "false") boolean withContent) {
        return chapterService.pageByBook(id, page, size, withContent);
    }

    @PostMapping("/books/{id}/chapters")
    public Chapter createChapter(@PathVariable("id") String id, @RequestBody Chapter chapter) {
        return chapterService.create(id, chapter);
    }

    @PutMapping("/chapters/{chapterId}")
    public Chapter updateChapter(@PathVariable("chapterId") String chapterId,
                                 @RequestBody Chapter chapter) {
        return chapterService.update(chapterId, chapter);
    }

    @DeleteMapping("/chapters/{chapterId}")
    public Map<String, Object> deleteChapter(@PathVariable("chapterId") String chapterId) {
        chapterService.delete(chapterId);
        return ok("已删除章节");
    }

    // ---------------- 书源 ----------------

    @GetMapping("/sources")
    public List<BookSource> listSources() {
        return bookSourceService.list(false);
    }

    @PostMapping("/sources")
    public BookSource createSource(@Valid @RequestBody BookSource source) {
        return bookSourceService.create(source);
    }

    @PutMapping("/sources/{id}")
    public BookSource updateSource(@PathVariable("id") String id, @RequestBody BookSource source) {
        return bookSourceService.update(id, source);
    }

    @DeleteMapping("/sources/{id}")
    public Map<String, Object> deleteSource(@PathVariable("id") String id) {
        bookSourceService.delete(id);
        return ok("已删除书源");
    }

    // ---------------- 版本发布 ----------------

    /** 读取指定平台的最新版本配置，供「版本发布」Tab 回填表单 */
    @GetMapping("/app/version")
    public AppVersion getAppVersion(
            @RequestParam(value = "platform", required = false, defaultValue = "android") String platform) {
        return appVersionService.latest(platform);
    }

    /** 保存（新增或更新）版本配置 */
    @PutMapping("/app/version")
    public AppVersion saveAppVersion(@RequestBody AppVersion version) {
        return appVersionService.save(version);
    }

    /**
     * 上传 APK 安装包：落 fyreader.apk-dir，返回文件名 / 大小 / MD5。
     * <p>落盘文件名做净化与防重名处理，同名不覆盖。</p>
     */
    @PostMapping("/app/apk")
    public Map<String, Object> uploadApk(@RequestParam("file") MultipartFile file) {
        return apkStorageService.upload(file);
    }

    /** 列出 apk-dir 内已有安装包（按修改时间倒序，current 标记当前客户端下载到的包） */
    @GetMapping("/app/apk/list")
    public Map<String, Object> listApks() {
        List<Map<String, Object>> records = apkStorageService.list();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", records.size());
        data.put("records", records);
        data.put("downloadUrl", "/api/v1/app/apk");
        return data;
    }

    /**
     * 章节详情：按书籍 + 章节取正文，供后台新窗口章节详情页使用。
     */
    @GetMapping("/books/{id}/chapters/{chapterId}/detail")
    public Map<String, Object> chapterDetail(@PathVariable("id") String id,
                                            @PathVariable("chapterId") String chapterId) {
        Book book = bookService.get(id);
        Chapter chapter = chapterService.getContent(id, chapterId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bookId", id);
        data.put("bookName", book == null ? "" : book.getName());
        data.put("author", book == null ? "" : book.getAuthor());
        data.put("chapterId", chapter.getId());
        data.put("chapterNo", chapter.getChapterNo());
        data.put("title", chapter.getTitle());
        data.put("wordCount", chapter.getWordCount());
        data.put("vip", chapter.getVip());
        data.put("updateTime", chapter.getUpdateTime());
        data.put("content", chapter.getContent());
        return data;
    }

    // ---------------- 导入 ----------------

    @PostMapping("/import/txt")
    public ImportResult importTxt(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "bookName", required = false) String bookName,
                                  @RequestParam(value = "author", required = false) String author,
                                  @RequestParam(value = "category", required = false) String category,
                                  @RequestParam(value = "encoding", required = false, defaultValue = "") String encoding) {
        return importService.importTxt(file, bookName, author, category, encoding);
    }

    private Map<String, Object> ok(String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", true);
        data.put("message", message);
        return data;
    }
}
