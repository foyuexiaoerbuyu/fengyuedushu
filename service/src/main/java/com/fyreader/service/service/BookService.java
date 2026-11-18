package com.fyreader.service.service;

import com.fyreader.service.auth.AuthContext;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * 分类聚合（管理端 / 部署者视角，全量统计）：
     * 返回 { total: 书籍总数, items: [{category, count}] }。
     */
    public Map<String, Object> categories() {
        return buildCategories(null, false);
    }

    /** 分类聚合（客户端视角）：只统计当前身份可见的书籍，匿名仅公开书 */
    public Map<String, Object> categoriesForClient() {
        return buildCategories(AuthContext.currentUserId(), true);
    }

    private Map<String, Object> buildCategories(String viewerId, boolean enforce) {
        List<Map<String, Object>> rows = bookMapper.selectCategoryStats(viewerId, enforce);
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        long total = 0L;
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            // H2/MySQL 对未加引号的列标签大小写处理不同，这里做一次兼容读取
            Object category = pick(row, "category", "CATEGORY");
            Object cnt = pick(row, "cnt", "CNT", "count", "COUNT");
            long count = cnt instanceof Number ? ((Number) cnt).longValue() : 0L;
            item.put("category", category == null ? "" : category.toString());
            item.put("count", count);
            items.add(item);
            total += count;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("items", items);
        return result;
    }

    /** 依次尝试多个 key，取第一个非空值（兼容不同数据库的列标签大小写）。 */
    private static Object pick(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
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

    /** 按书名精确查询，未命中返回 null（TXT 重导复用书籍 ID 用） */
    public Book getByName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return bookMapper.selectByName(name.trim());
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

    // ------------------------------------------------------------------
    // 可见性与归属：客户端读接口、用户级写接口统一走这里，避免"列表过滤但详情可按 ID 直读"
    // ------------------------------------------------------------------

    /** 客户端书籍列表：强制按当前身份收敛可见性，前端传 viewerId/visibilityOnly 一律被覆盖 */
    public PageResult<Book> pageForClient(BookQuery query) {
        BookQuery q = query == null ? new BookQuery() : query;
        q.setVisibilityOnly(Boolean.TRUE);
        q.setViewerId(AuthContext.currentUserId());
        return page(q);
    }

    /**
     * 客户端读取单本书（详情 / 目录 / 正文的统一入口）。
     * 不可见时抛 403，避免通过书籍 ID 直读他人私有书。
     */
    public Book getReadable(String id) {
        Book book = get(id);
        assertReadable(book);
        return book;
    }

    /** 读权限校验：部署者全放行；公开书放行；私有书仅归属用户可读 */
    public void assertReadable(Book book) {
        if (book == null) {
            throw BusinessException.notFound("书籍不存在");
        }
        AuthContext ctx = AuthContext.current();
        if (ctx.isAdmin()) {
            return;
        }
        if (book.isPublicVisibility()) {
            return;
        }
        String userId = ctx.getUserId();
        if (userId != null && book.isOwnedBy(userId)) {
            return;
        }
        throw new BusinessException(403, "无权访问该书籍：这是他人的私有书籍");
    }

    /** 写权限校验：部署者全放行；登录用户仅能操作自己名下的书，匿名一律拒绝 */
    public void assertWritable(Book book) {
        if (book == null) {
            throw BusinessException.notFound("书籍不存在");
        }
        AuthContext ctx = AuthContext.current();
        if (ctx.isAdmin()) {
            return;
        }
        String userId = ctx.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录后再操作");
        }
        if (book.isOwnedBy(userId)) {
            return;
        }
        throw new BusinessException(403, "无权操作他人的书籍");
    }

    /** 我的书籍：只按归属用户过滤，含私有书 */
    public PageResult<Book> pageMine(String ownerId, String keyword, Integer pageNum, Integer sizeNum) {
        if (!StringUtils.hasText(ownerId)) {
            throw new BusinessException(401, "请先登录后再查看我的书籍");
        }
        int size = (sizeNum == null || sizeNum <= 0) ? properties.getDefaultPageSize()
                : Math.min(sizeNum, properties.getMaxPageSize());
        int page = (pageNum == null || pageNum <= 0) ? 1 : pageNum;
        long total = bookMapper.countByOwner(ownerId, keyword);
        if (total == 0) {
            return PageResult.empty(page, size);
        }
        List<Book> records = bookMapper.selectByOwner(ownerId, keyword, (page - 1) * size, size);
        return new PageResult<>(records, total, page, size);
    }

    /** 切换可见性：PUBLIC / PRIVATE，非法值统一按私有处理（更安全） */
    @Transactional(rollbackFor = Exception.class)
    public Book switchVisibility(String id, String visibility) {
        Book book = get(id);
        assertWritable(book);
        String target = Book.VISIBILITY_PUBLIC.equalsIgnoreCase(visibility)
                ? Book.VISIBILITY_PUBLIC : Book.VISIBILITY_PRIVATE;
        bookMapper.updateVisibility(id, target);
        return bookMapper.selectById(id);
    }

    /** 删除自己的书（含章节级联），非本人名下的书拒绝 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteOwned(String id) {
        Book book = get(id);
        assertWritable(book);
        chapterMapper.deleteByBookId(id);
        bookMapper.deleteById(id);
    }

    /** 某用户名下的同名书籍，命中则复用原书籍 ID，避免误覆盖他人同名书 */
    public Book findOwnedBook(String ownerId, String name) {
        if (!StringUtils.hasText(ownerId) || !StringUtils.hasText(name)) {
            return null;
        }
        return bookMapper.selectByNameAndOwner(name.trim(), ownerId);
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
