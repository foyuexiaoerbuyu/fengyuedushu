/*
 * This file is part of FYReader.
 * FYReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FYReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with FYReader.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2020 - 2022 fengyuecanzhu
 */

package xyz.fycz.myreader.webapi.server

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import okhttp3.Request
import xyz.fycz.myreader.common.APPCONST
import xyz.fycz.myreader.entity.SearchBookBean
import xyz.fycz.myreader.greendao.entity.Book
import xyz.fycz.myreader.greendao.entity.Chapter
import xyz.fycz.myreader.model.mulvalmap.ConMVMap
import xyz.fycz.myreader.util.help.StringHelper
import xyz.fycz.myreader.util.utils.OkHttpUtils
import java.io.IOException
import java.lang.reflect.Type
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 自建服务端（fyreader-service）客户端。
 *
 * 目标：把书籍、目录、正文的获取方式从「客户端抓网页 + 规则解析」切换为「后端 /api/v1 接口」。
 *
 * 设计约束：
 * 1. 全部分支复用 [OkHttpUtils.getOkHttpClient]，与原有书源链路共享代理设置，不引入 Retrofit；
 * 2. 返回 RxJava [Observable]，与 [xyz.fycz.myreader.webapi.BookApi] 现有签名保持一致，
 *    上层 ViewModel 无需改动；
 * 3. 服务端字符串主键直接落到 App 端 Book.id / Chapter.id（App 端两者本就是 String），
 *    因此详情、目录、正文可以用同一个 id 串起来，不需要额外做 id 映射表。
 *
 * @author fengyue
 */
object ServerApi {

    private const val DEFAULT_PAGE_SIZE = 20

    /** 服务端章节主键前缀（fycz-service 生成形如 `c_xxx` 的字符串主键）。 */
    private const val SERVER_CHAPTER_ID_PREFIX = "c_"

    /** 发现页分类 url 中携带分类名的查询参数前缀。 */
    private const val CATEGORY_QUERY_PREFIX = "?category="

    private val gson = Gson()

    private val typeBookPage = object : TypeToken<ServerResult<ServerPage<ServerBook>>>() {}.type
    private val typeChapterList = object : TypeToken<ServerResult<List<ServerChapter>>>() {}.type
    private val typeBook = object : TypeToken<ServerResult<ServerBook>>() {}.type
    private val typeChapter = object : TypeToken<ServerResult<ServerChapter>>() {}.type
    private val typeCategoryResult = object : TypeToken<ServerResult<ServerCategoryResult>>() {}.type
    private val typeAppUpdate = object : TypeToken<ServerResult<ServerAppUpdate>>() {}.type

    // ************************************************************
    // 对外接口
    // ************************************************************

    /**
     * 判断某个 source / sourceUrl 是否指向自建服务端。
     */
    @JvmStatic
    fun isServerSource(source: String?): Boolean {
        if (source.isNullOrEmpty()) return false
        return source == APPCONST.SERVER_SOURCE_TAG
                || source.startsWith(APPCONST.SERVER_SOURCE_TAG)
                || source == APPCONST.SERVER_SOURCE_NAME
    }

    /**
     * 健康检查，用于在设置页验证「App → 服务端」是否连通。
     */
    @JvmStatic
    fun health(): Observable<String> = Observable.fromCallable {
        val body = getJson("/health", withToken = false)
        if (body.contains("UP")) "服务端连接正常" else throw IOException("健康检查响应异常：${body.take(200)}")
    }.subscribeOn(Schedulers.io())

    /**
     * 搜索书籍。
     *
     * 注意：服务端搜索结果即本地书库内容，因此搜索结果等价于「书库检索」。
     */
    @JvmStatic
    @JvmOverloads
    fun search(key: String, page: Int = 1, size: Int = DEFAULT_PAGE_SIZE): Observable<ConMVMap<SearchBookBean, Book>> =
            Observable.fromCallable {
                val encoded = URLEncoder.encode(key, "UTF-8")
                val json = getJson("/books?keyword=$encoded&page=$page&size=$size")
                val pageData: ServerPage<ServerBook> = parseResult(json, typeBookPage)
                val bookMap = ConMVMap<SearchBookBean, Book>()
                pageData.records?.forEach { src ->
                    val book = toAppBook(src) ?: return@forEach
                    bookMap.add(SearchBookBean(book.name, book.author), book)
                }
                bookMap
            }.subscribeOn(Schedulers.io())

    /**
     * 发现/书库列表（按分页拉取，可按分类过滤）。
     *
     * category 为空表示「全部书籍」；非空时后端按 t_book.category 精确过滤。
     */
    @JvmStatic
    @JvmOverloads
    fun findBooks(page: Int = 1, category: String? = null, size: Int = DEFAULT_PAGE_SIZE): Observable<List<Book>> =
            Observable.fromCallable {
                val sb = StringBuilder("/books?page=").append(page).append("&size=").append(size)
                if (!category.isNullOrBlank()) {
                    sb.append("&category=").append(URLEncoder.encode(category, "UTF-8"))
                }
                val json = getJson(sb.toString())
                val pageData: ServerPage<ServerBook> = parseResult(json, typeBookPage)
                pageData.records?.mapNotNull { toAppBook(it) } ?: emptyList()
            }.subscribeOn(Schedulers.io())

    /**
     * 分类聚合列表（分类名 + 数量），供发现页生成分类 Tab。
     */
    @JvmStatic
    fun findCategories(): Observable<List<ServerCategory>> = Observable.fromCallable {
        val json = getJson("/categories")
        val result: ServerCategoryResult = parseResult(json, typeCategoryResult)
        result.items ?: emptyList()
    }.subscribeOn(Schedulers.io())

    /**
     * 阻塞式获取分类列表：供 FindCrawler.initData() 在 io 线程内同步构建分类 Tab。
     * 服务端不可达时返回空列表，由调用方回退到「全部书籍」单分类。
     */
    @JvmStatic
    fun findCategoriesBlocking(): List<ServerCategory> = try {
        findCategories().blockingFirst()
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * 从发现页分类 url 中解析分类名：`fyreader://server?category=玄幻`。
     */
    @JvmStatic
    fun categoryOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val idx = url.indexOf(CATEGORY_QUERY_PREFIX)
        if (idx < 0) return null
        return try {
            URLDecoder.decode(url.substring(idx + CATEGORY_QUERY_PREFIX.length), "UTF-8")
        } catch (e: Exception) {
            url.substring(idx + CATEGORY_QUERY_PREFIX.length)
        }
    }

    /**
     * 获取书籍详情。
     *
     * 采用「就地回填」而不是新建对象：调用方传入的 Book 通常已经入库（带分组、阅读进度等
     * 本地字段），就地赋值可避免把本地字段冲掉。
     */
    @JvmStatic
    fun getBookInfo(book: Book): Observable<Book> = Observable.fromCallable {
        val bookId = requireBookId(book)
        val json = getJson("/books/$bookId")
        val src: ServerBook = parseResult(json, typeBook)
        applyBookInfo(book, src)
        book
    }.subscribeOn(Schedulers.io())

    /**
     * 获取完整目录。
     *
     * 使用 /catalog 一次取全量章节，避免分页拼接；该接口不返回正文。
     */
    @JvmStatic
    fun getChapterList(book: Book): Observable<List<Chapter>> = Observable.fromCallable {
        val bookId = requireBookId(book)
        val json = getJson("/books/$bookId/catalog")
        val list: List<ServerChapter> = parseResult(json, typeChapterList)
        list.mapIndexed { index, src -> toAppChapter(src, bookId, index) }
    }.subscribeOn(Schedulers.io())

    /**
     * 获取章节正文。
     *
     * 优先用服务端章节 id 直接取；没有 id 时退回按章节序号取。
     */
    @JvmStatic
    fun getChapterContent(chapter: Chapter, book: Book): Observable<String> = Observable.fromCallable {
        val bookId = requireBookId(book)
        val serverChapterId = resolveServerChapterId(chapter)
        val json = if (!serverChapterId.isNullOrEmpty()) {
            getJson("/books/$bookId/chapters/$serverChapterId")
        } else {
            getJson("/books/$bookId/chapters/no/${chapter.number + 1}")
        }
        val src: ServerChapter = parseResult(json, typeChapter)
        val content = src.content
        if (content.isNullOrEmpty()) {
            throw IOException("服务端未返回章节正文（第 ${chapter.number} 章）")
        }
        content
    }.subscribeOn(Schedulers.io())

    /**
     * App 更新检查：走自建服务端 `/app/update`，替代原语雀 / GitLab 更新源。
     *
     * @param versionCode 客户端当前 versionCode，服务端据此计算 hasUpdate / 强制更新
     */
    @JvmStatic
    @JvmOverloads
    fun checkAppUpdate(versionCode: Int, platform: String = "android"): Observable<ServerAppUpdate> =
            Observable.fromCallable {
                val json = getJson("/app/update?platform=$platform&versionCode=$versionCode")
                parseResult<ServerAppUpdate>(json, typeAppUpdate)
            }.subscribeOn(Schedulers.io())

    /**
     * 阻塞式检查更新：供 [xyz.fycz.myreader.application.App.checkVersionByServer] 在子线程内直接调用。
     * 服务端不可达时抛出异常，由调用方回退到原第三方更新源。
     */
    @JvmStatic
    @JvmOverloads
    fun checkAppUpdateBlocking(versionCode: Int, platform: String = "android"): ServerAppUpdate =
            checkAppUpdate(versionCode, platform).blockingFirst()

    // ************************************************************
    // HTTP
    // ************************************************************

    @Throws(IOException::class)
    private fun getJson(path: String, withToken: Boolean = true): String {
        val url = ServerConfig.apiUrl(path)
        val builder = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", APPCONST.DEFAULT_USER_AGENT)
        if (withToken) {
            builder.addHeader("X-FY-Token", ServerConfig.getToken())
        }
        val response = OkHttpUtils.getOkHttpClient().newCall(builder.build()).execute()
        response.use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}：${body.take(300)}")
            }
            if (body.isBlank()) {
                throw IOException("服务端返回空响应")
            }
            return body
        }
    }

    // ************************************************************
    // 解析与映射
    // ************************************************************

    /**
     * 拆开后端统一响应 [ServerResult]，code 非 0 视为业务失败。
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IOException::class)
    private fun <T> parseResult(json: String, type: Type): T {
        val result: ServerResult<T> = gson.fromJson(json, type)
                ?: throw IOException("服务端返回内容无法解析")
        if (result.code != 0) {
            throw IOException("服务端返回错误 [${result.code}] ${result.message ?: "未知原因"}")
        }
        return result.data ?: throw IOException("服务端未返回数据")
    }

    @Throws(IOException::class)
    private fun requireBookId(book: Book): String {
        val id = book.id
        if (id.isNullOrEmpty()) {
            throw IOException("书籍 id 为空，无法请求服务端")
        }
        return id
    }

    private fun toAppBook(src: ServerBook): Book? {
        val name = src.name
        if (name.isNullOrEmpty()) return null
        val book = Book()
        book.id = src.id ?: StringHelper.getStringRandom(25)
        book.name = name
        book.author = src.author ?: ""
        book.imgUrl = ServerConfig.absoluteUrl(src.coverUrl)
        book.type = src.category ?: ""
        book.desc = src.intro ?: ""
        book.status = src.status ?: ""
        book.wordCount = src.wordCount ?: ""
        book.newestChapterId = src.latestChapterId ?: ""
        book.newestChapterTitle = src.latestChapterName ?: ""
        book.chapterTotalNum = src.chapterCount ?: 0
        book.infoUrl = src.infoUrl ?: ""
        book.chapterUrl = ""
        book.updateDate = src.updateTime ?: ""
        book.source = APPCONST.SERVER_SOURCE_TAG
        return book
    }

    /**
     * 把服务端书籍信息回填到已存在的 Book 对象，保留本地分组、进度等字段。
     */
    private fun applyBookInfo(target: Book, src: ServerBook) {
        src.id?.let { target.id = it }
        src.name?.let { if (it.isNotEmpty()) target.name = it }
        src.author?.let { target.author = it }
        src.coverUrl?.let { target.imgUrl = ServerConfig.absoluteUrl(it) }
        src.category?.let { target.type = it }
        src.intro?.let { target.desc = it }
        src.status?.let { target.status = it }
        src.wordCount?.let { target.wordCount = it }
        src.latestChapterId?.let { target.newestChapterId = it }
        src.latestChapterName?.let { target.newestChapterTitle = it }
        src.chapterCount?.let { target.chapterTotalNum = it }
        src.infoUrl?.let { target.infoUrl = it }
        src.updateTime?.let { target.updateDate = it }
        target.source = APPCONST.SERVER_SOURCE_TAG
    }

    /**
     * 解析可用的服务端章节 ID。
     *
     * 章节落本地库后 [Chapter.id] 会被 GreenDAO 主键覆盖，不能再当服务端 ID 用；
     * 因此按 [Chapter.url]（保存了服务端 ID）→ [Chapter.id]（直接来自目录接口时仍有效）
     * 的顺序取，只有形如 `c_xxx` 的服务端 ID 才参与匹配，取不到则返回 null，
     * 由调用方退回按章节序号请求。
     */
    private fun resolveServerChapterId(chapter: Chapter): String? {
        val fromUrl = chapter.url
        if (!fromUrl.isNullOrEmpty() && fromUrl.startsWith(SERVER_CHAPTER_ID_PREFIX)) {
            return fromUrl
        }
        val fromId = chapter.id
        if (!fromId.isNullOrEmpty() && fromId.startsWith(SERVER_CHAPTER_ID_PREFIX)) {
            return fromId
        }
        return null
    }

    private fun toAppChapter(src: ServerChapter, bookId: String, index: Int = -1): Chapter {
        val chapter = Chapter()
        chapter.id = src.id ?: ""
        chapter.bookId = src.bookId ?: bookId
        // App 端章节序号是 0-based 的列表下标（对比 BookChapterList 的 number = index），
        // 服务端 chapterNo 从 1 开始，这里统一按下标赋值，否则阅读页按 number 对位会一直停在加载态。
        chapter.number = if (index >= 0) index else Math.max(0, (src.chapterNo ?: 1) - 1)
        chapter.title = src.title ?: ""
        // 本地库会用 GreenDAO 主键覆盖 chapter.id，这里额外用 url 记住服务端章节 ID，
        // 拉正文时优先用它，避免本地主键被当成服务端 ID 请求而 404。
        chapter.url = src.id ?: ""
        chapter.content = src.content
        chapter.updateTime = src.updateTime ?: ""
        chapter.isVip = (src.vip ?: 0) == 1
        return chapter
    }
}
