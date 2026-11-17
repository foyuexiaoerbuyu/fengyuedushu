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

/**
 * 自建服务端（fyreader-service）接口数据结构。
 *
 * 全部字段使用可空类型：Gson 基于 Unsafe 分配对象、不经过构造函数，
 * 字段缺失时会被直接置为 null，非空声明只会在运行期造成 NPE。
 *
 * @author fengyue
 */

/**
 * 统一响应包装，对应后端 com.fyreader.service.common.Result
 */
data class ServerResult<T>(
        val code: Int = -1,
        val message: String? = null,
        val data: T? = null,
        val timestamp: Long = 0
)

/**
 * 分页包装，对应后端 com.fyreader.service.common.PageResult
 */
data class ServerPage<T>(
        val records: List<T>? = null,
        val total: Long = 0,
        val page: Int = 1,
        val size: Int = 20,
        val pages: Int = 0
)

/**
 * 服务端书籍，对应后端 com.fyreader.service.entity.Book
 */
data class ServerBook(
        val id: String? = null,
        val name: String? = null,
        val author: String? = null,
        val coverUrl: String? = null,
        val category: String? = null,
        val intro: String? = null,
        val status: String? = null,
        val wordCount: String? = null,
        val latestChapterId: String? = null,
        val latestChapterName: String? = null,
        val chapterCount: Int? = null,
        val sourceName: String? = null,
        val sourceUrl: String? = null,
        val infoUrl: String? = null,
        val remark: String? = null,
        val createTime: String? = null,
        val updateTime: String? = null
)

/**
 * 服务端章节，对应后端 com.fyreader.service.entity.Chapter
 * <p>
 * content 仅在正文接口中返回，目录接口返回时为空。
 */
data class ServerChapter(
        val id: String? = null,
        val bookId: String? = null,
        val chapterNo: Int? = null,
        val title: String? = null,
        val content: String? = null,
        val wordCount: Int? = null,
        val vip: Int? = null,
        val createTime: String? = null,
        val updateTime: String? = null
)

/**
 * 分类聚合项，对应后端 /api/v1/categories 的 items 元素
 */
data class ServerCategory(
        val category: String? = null,
        val count: Long = 0
)

/**
 * 分类聚合响应，对应后端 BookService.categories()
 */
data class ServerCategoryResult(
        val total: Long = 0,
        val items: List<ServerCategory>? = null
)

/**
 * 健康检查响应
 */
data class ServerHealth(
        val status: String? = null,
        val version: String? = null,
        val time: String? = null
)
