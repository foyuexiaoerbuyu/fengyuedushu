package com.fyreader.service.controller;

import com.fyreader.service.common.PageResult;
import com.fyreader.service.config.FyReaderProperties;
import com.fyreader.service.dto.BookQuery;
import com.fyreader.service.entity.AppVersion;
import com.fyreader.service.entity.Book;
import com.fyreader.service.entity.BookGroup;
import com.fyreader.service.entity.BookSource;
import com.fyreader.service.entity.Chapter;
import com.fyreader.service.mapper.BookGroupMapper;
import com.fyreader.service.service.AppVersionService;
import com.fyreader.service.service.BookService;
import com.fyreader.service.service.BookSourceService;
import com.fyreader.service.service.ChapterService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
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
    private final AppVersionService appVersionService;
    private final FyReaderProperties properties;

    public OpenApiController(BookService bookService, ChapterService chapterService,
                             BookSourceService bookSourceService, BookGroupMapper bookGroupMapper,
                             AppVersionService appVersionService, FyReaderProperties properties) {
        this.bookService = bookService;
        this.chapterService = chapterService;
        this.bookSourceService = bookSourceService;
        this.bookGroupMapper = bookGroupMapper;
        this.appVersionService = appVersionService;
        this.properties = properties;
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

    /**
     * App 更新检查，App 端唯一的更新来源。
     *
     * @param platform        平台标识，默认 android
     * @param clientVersionCode 客户端当前 versionCode，用于计算 hasUpdate / 强制更新
     */
    @GetMapping("/app/update")
    public Map<String, Object> appUpdate(
            @RequestParam(value = "platform", required = false, defaultValue = "android") String platform,
            @RequestParam(value = "versionCode", required = false, defaultValue = "0") int clientVersionCode) {
        AppVersion latest = appVersionService.latest(platform);
        Map<String, Object> data = new LinkedHashMap<>();
        if (latest == null) {
            data.put("hasUpdate", false);
            data.put("versionCode", 0);
            data.put("versionName", "");
            data.put("forceUpdate", false);
            data.put("minVersionCode", 0);
            data.put("downloadUrl", "");
            data.put("updateContent", "");
            return data;
        }
        int serverVersionCode = latest.getVersionCode() == null ? 0 : latest.getVersionCode();
        int minVersionCode = latest.getMinVersionCode() == null ? 0 : latest.getMinVersionCode();
        boolean forceFlag = latest.getForceUpdate() != null && latest.getForceUpdate() == 1;
        // 低于 min_version_code 的客户端强制更新；客户端未上报版本号时以 force_update 为准
        boolean forceUpdate = forceFlag
                || (clientVersionCode > 0 && minVersionCode > 0 && clientVersionCode < minVersionCode);
        // 下发前再规范化一次：只放行站内路径，其余一律回落到本服务的下载接口
        String downloadUrl = normalizeDownloadUrl(latest.getDownloadUrl());
        if (downloadUrl.isEmpty()) {
            downloadUrl = "/api/v1/app/apk";
        }

        data.put("hasUpdate", serverVersionCode > clientVersionCode);
        data.put("versionCode", serverVersionCode);
        data.put("versionName", latest.getVersionName() == null ? "" : latest.getVersionName());
        data.put("forceUpdate", forceUpdate);
        data.put("minVersionCode", minVersionCode);
        data.put("downloadUrl", downloadUrl);
        data.put("updateContent", latest.getUpdateContent() == null ? "" : latest.getUpdateContent());
        data.put("domain", latest.getDomain() == null ? "" : latest.getDomain());
        data.put("pluginConfigUrl", latest.getPluginConfigUrl() == null ? "" : latest.getPluginConfigUrl());
        data.put("splashImageUrl", latest.getSplashImageUrl() == null ? "" : latest.getSplashImageUrl());
        data.put("splashImageMd5", latest.getSplashImageMd5() == null ? "" : latest.getSplashImageMd5());
        return data;
    }

    /**
     * 下载最新 APK：从 apk-dir 中取修改时间最新的 .apk 流式返回。
     * <p>直接返回 {@link ResponseEntity}，绕过 {@code ApiResponseAdvice} 的 JSON 包装。</p>
     */
    @GetMapping("/app/apk")
    public ResponseEntity<Resource> appApk() {
        File apk = latestApk();
        if (apk == null) {
            return ResponseEntity.notFound().build();
        }
        FileSystemResource resource = new FileSystemResource(apk);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + apk.getName() + "\"")
                .contentLength(apk.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * 下载地址白名单（读路径版）：只接受以单个 "/" 开头、不含反斜杠与 ".." 的站内路径，
     * 其余（含 http(s):// 外部地址、协议相对地址）返回空串，由调用方回落到站内下载接口。
     */
    private String normalizeDownloadUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String url = raw.trim();
        if (!url.startsWith("/") || url.startsWith("//")
                || url.contains("\\") || url.contains("..")) {
            return "";
        }
        return url;
    }

    /** 取 apk 目录下最近修改的 .apk，无文件返回 null */
    private File latestApk() {
        String dir = StringUtils.hasText(properties.getApkDir()) ? properties.getApkDir() : "./data/apk";
        File folder = new File(dir);
        if (!folder.isDirectory()) {
            return null;
        }
        File[] apks = folder.listFiles((d, name) -> name.toLowerCase().endsWith(".apk"));
        if (apks == null || apks.length == 0) {
            return null;
        }
        return Arrays.stream(apks)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
    }
}
