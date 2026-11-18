package xyz.fycz.myreader.util;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import xyz.fycz.myreader.greendao.entity.Chapter;
import xyz.fycz.myreader.util.help.StringHelper;

/**
 * 章节下载统一调度器
 * <p>
 * 阅读页章节预取（{@code NetPageLoader}）与「缓存全部」（{@code ReadActivity}）原本是两条
 * 互不可见的下载流水线：各自判重、各自并发请求、共用同一个非原子写盘方法，导致同一章节
 * 可能被重复请求、内容文件被同时截断。
 * <p>
 * 这里提供两件东西：
 * 1. 进程级单线程调度器：所有章节网络请求与写盘都跑在同一个线程上，天然串行；
 * 2. 进程级在途集合：键为「bookId@章节号」，两条流水线共享，发起前 mark，成功/失败后 finish。
 */
public class ChapterDownloadQueue {

    private static final String THREAD_NAME = "chapter-download";

    /**
     * 章节下载专用单线程，保证任意时刻只有一个章节请求在执行
     */
    private static final ExecutorService SINGLE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, THREAD_NAME);
        // 略低于普通线程优先级，避免整本缓存时抢占阅读页渲染
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.setDaemon(true);
        return thread;
    });

    private static final Scheduler SINGLE_SCHEDULER = Schedulers.from(SINGLE_EXECUTOR);

    /**
     * 正在请求中的章节键集合（bookId@number）
     */
    private static final Set<String> LOADING_CHAPTERS =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private ChapterDownloadQueue() {
    }

    /**
     * 章节请求使用的串行调度器
     */
    public static Scheduler scheduler() {
        return SINGLE_SCHEDULER;
    }

    /**
     * 章节请求使用的单线程执行器
     */
    public static ExecutorService executor() {
        return SINGLE_EXECUTOR;
    }

    private static String key(Chapter chapter, String fallbackBookId) {
        if (chapter == null) {
            return "";
        }
        String bookId = chapter.getBookId();
        if (StringHelper.isEmpty(bookId)) {
            bookId = fallbackBookId;
        }
        return (bookId == null ? "" : bookId) + "@" + chapter.getNumber();
    }

    /**
     * 该章节是否已有请求在途
     */
    public static boolean isChapterLoading(Chapter chapter, String fallbackBookId) {
        return LOADING_CHAPTERS.contains(key(chapter, fallbackBookId));
    }

    /**
     * 标记该章节已发起请求；返回 true 表示本次抢占成功，false 表示已有请求在途
     */
    public static boolean markChapterLoading(Chapter chapter, String fallbackBookId) {
        return LOADING_CHAPTERS.add(key(chapter, fallbackBookId));
    }

    /**
     * 请求结束（成功或失败）后移除在途标记
     */
    public static void finishChapterLoading(Chapter chapter, String fallbackBookId) {
        LOADING_CHAPTERS.remove(key(chapter, fallbackBookId));
    }
}
