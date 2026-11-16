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

package xyz.fycz.dynamic.fix

import me.fycz.maple.MapleBridge
import me.fycz.maple.MapleUtils
import me.fycz.maple.MethodReplacement
import xyz.fycz.myreader.greendao.DbManager
import xyz.fycz.myreader.greendao.entity.rule.BookSource
import xyz.fycz.myreader.greendao.gen.BookSourceDao
import xyz.fycz.myreader.model.sourceAnalyzer.BookSourceManager

/**
 * @author fengyue
 * @date 2022/3/31 9:21
 */
@AppFix([243], ["修复软件无法打开的问题(超时时间为5s)", "修复DIY书源重复显示订阅书源的问题"], "2022-03-31")
class App243Fix : AppFixHandle {

    override fun onFix(key: String): BooleanArray {
        return handleFix(
            key,
            "getAllNoLocalSource" to { fixGetAllNoLocalSource() },
        )
    }

    private fun getAllNoLocalSource(): List<BookSource> {
        return DbManager.getDaoSession().bookSourceDao.queryBuilder()
            .where(BookSourceDao.Properties.SourceEName.isNull)
            .where(BookSourceDao.Properties.SourceType.isNotNull)
            .orderAsc(BookSourceDao.Properties.OrderNum)
            .list()
    }


    private fun fixGetAllNoLocalSource() {
        MapleUtils.findAndHookMethod(
            BookSourceManager::class.java,
            "getAllNoLocalSource",
            object : MethodReplacement() {
                override fun replaceHookedMethod(param: MapleBridge.MethodHookParam): Any {
                    return getAllNoLocalSource()
                }
            }
        )
    }
}