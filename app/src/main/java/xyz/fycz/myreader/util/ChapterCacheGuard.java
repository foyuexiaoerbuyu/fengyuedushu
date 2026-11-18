package xyz.fycz.myreader.util;

import xyz.fycz.myreader.greendao.entity.Chapter;
import xyz.fycz.myreader.greendao.service.ChapterService;
import xyz.fycz.myreader.util.help.StringHelper;

/**
 * 章节缓存统一判重门面
 * <p>
 * 阅读页预取（NetPageLoader）、阅读页「缓存全部」（ReadActivity）、
 * 书架一键缓存（BookcasePresenter）三条入口原本各自判重：
 * 一处只看内存 content、一处只看缓存文件、一处完全没接在途集合，
 * 于是同一章节可能被重复请求，进度统计也会因为口径不同而错乱。
 * 这里把「是否需要下载 / 是否已在途 / 抢占与释放」收敛成唯一口径，
 * 三个入口只允许通过本门面判断，避免再次分叉。
 */
public final class ChapterCacheGuard {

    private ChapterCacheGuard() {
    }

    /**
     * 章节是否已有正文（content 存的是缓存文件路径，为空即未缓存）
     */
    public static boolean hasContent(Chapter chapter) {
        return chapter != null && !StringHelper.isEmpty(chapter.getContent());
    }

    /**
     * 章节缓存文件是否已存在（新旧两种命名都会被识别）
     */
    public static boolean isCached(Chapter chapter) {
        return chapter != null && ChapterService.isChapterCached(chapter);
    }

    /**
     * 该章节是否已有请求在途（可能来自阅读页预取，也可能来自缓存全部）
     */
    public static boolean isDownloading(Chapter chapter, String fallbackBookId) {
        return ChapterDownloadQueue.isChapterLoading(chapter, fallbackBookId);
    }

    /**
     * 章节是否还需要下载：无正文、无缓存文件、且没有在途请求
     */
    public static boolean needDownload(Chapter chapter, String fallbackBookId) {
        return chapter != null
                && !hasContent(chapter)
                && !isCached(chapter)
                && !isDownloading(chapter, fallbackBookId);
    }

    /**
     * 原子抢占章节下载权，抢占成功才允许发起网络请求。
     * 已缓存或已有在途请求时返回 false，调用方必须直接跳过。
     */
    public static boolean tryAcquire(Chapter chapter, String fallbackBookId) {
        if (chapter == null || isCached(chapter)) {
            return false;
        }
        return ChapterDownloadQueue.markChapterLoading(chapter, fallbackBookId);
    }

    /**
     * 释放章节下载权，成功 / 失败 / 订阅被取消三条路径都必须调用
     */
    public static void release(Chapter chapter, String fallbackBookId) {
        ChapterDownloadQueue.finishChapterLoading(chapter, fallbackBookId);
    }
}
