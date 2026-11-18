package com.fyreader.service.controller;

import com.fyreader.service.auth.AuthContext;
import com.fyreader.service.common.BusinessException;
import com.fyreader.service.common.PageResult;
import com.fyreader.service.dto.ImportResult;
import com.fyreader.service.entity.Book;
import com.fyreader.service.service.BookService;
import com.fyreader.service.service.ImportService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录用户的书架接口（PC 网页用户页与 App 共用）。
 *
 * <p>与 {@code /api/v1/books}（只读、按可见性可见）不同，这里的接口都以"当前登录用户"
 * 为操作主体：未登录一律 401，越权操作他人书籍一律 403。</p>
 */
@RestController
@RequestMapping("/api/v1/me")
public class UserBookController {

    private final BookService bookService;
    private final ImportService importService;

    public UserBookController(BookService bookService, ImportService importService) {
        this.bookService = bookService;
        this.importService = importService;
    }

    /** 我的书籍（含私有），支持关键字过滤与分页 */
    @GetMapping("/books")
    public PageResult<Book> myBooks(@RequestParam(value = "keyword", required = false) String keyword,
                                    @RequestParam(value = "page", required = false) Integer page,
                                    @RequestParam(value = "size", required = false) Integer size) {
        return bookService.pageMine(requireUserId(), keyword, page, size);
    }

    /**
     * 上传 TXT 书籍：归属当前用户，可指定 visibility=PUBLIC|PRIVATE（默认 PRIVATE，
     * 用户确认要公开时再切换，避免误公开）。
     */
    @PostMapping("/books/txt")
    public ImportResult uploadTxt(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "bookName", required = false) String bookName,
                                 @RequestParam(value = "author", required = false) String author,
                                 @RequestParam(value = "category", required = false) String category,
                                 @RequestParam(value = "encoding", required = false) String encoding,
                                 @RequestParam(value = "visibility", required = false,
                                         defaultValue = Book.VISIBILITY_PRIVATE) String visibility) {
        String userId = requireUserId();
        return importService.importTxt(file, bookName, author, category, encoding, userId, visibility);
    }

    /** 切换书籍可见性：PUBLIC（匿名可读）/ PRIVATE（仅自己可读） */
    @PutMapping("/books/{id}/visibility")
    public Book switchVisibility(@PathVariable("id") String id,
                                 @RequestParam("visibility") String visibility) {
        requireUserId();
        return bookService.switchVisibility(id, visibility);
    }

    /** 删除自己的书籍（连带章节） */
    @DeleteMapping("/books/{id}")
    public Map<String, Object> delete(@PathVariable("id") String id) {
        requireUserId();
        bookService.deleteOwned(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bookId", id);
        data.put("deleted", true);
        return data;
    }

    /** 取当前登录用户 ID，未登录直接 401（避免"匿名空参"被当成某个用户处理） */
    private static String requireUserId() {
        String userId = AuthContext.currentUserId();
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }
}
