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

package xyz.fycz.myreader.webapi.server;

import android.util.Log;

import xyz.fycz.myreader.common.APPCONST;
import xyz.fycz.myreader.greendao.DbManager;
import xyz.fycz.myreader.greendao.entity.rule.BookSource;
import xyz.fycz.myreader.greendao.entity.rule.ContentRule;
import xyz.fycz.myreader.greendao.entity.rule.FindRule;
import xyz.fycz.myreader.greendao.entity.rule.InfoRule;
import xyz.fycz.myreader.greendao.entity.rule.SearchRule;
import xyz.fycz.myreader.greendao.entity.rule.TocRule;
import xyz.fycz.myreader.model.sourceAnalyzer.BookSourceManager;

/**
 * 「本机书库」书源注册器。
 * <p>
 * 服务端已经取代了客户端抓网页，但 App 的搜索、书源选择、书籍来源标记都依赖
 * {@link BookSource} 记录。这里在首次启动时自动写入一条指向自建服务端的书源，
 * 让服务端书源可以像普通书源一样被勾选、被搜索，而不需要用户手工新建。
 * <p>
 * 只注册一次：注册成功后打上标记，用户之后把它删掉也不会再自动加回来。
 *
 * @author fengyue
 */
public final class ServerSourceInstaller {

    private static final String TAG = "ServerSourceInstaller";

    private static final String SOURCE_E_NAME = "fyreader_server";
    private static final String SOURCE_GROUP = "内置书源";

    private ServerSourceInstaller() {
    }

    /**
     * 若尚未注册过「本机书库」书源，则写入一条。
     *
     * @return 本次是否真的写入了
     */
    public static boolean ensureSource() {
        if (ServerConfig.isSourceEnsured()) {
            return false;
        }
        try {
            if (isSourceExist()) {
                ServerConfig.setSourceEnsured(true);
                return false;
            }
            BookSource source = buildSource();
            boolean added = BookSourceManager.addBookSource(source);
            ServerConfig.setSourceEnsured(added);
            Log.d(TAG, "ensureSource added=" + added);
            return added;
        } catch (Exception e) {
            // 书源注册失败不应阻断 App 启动
            Log.e(TAG, "ensureSource failed", e);
            return false;
        }
    }

    private static boolean isSourceExist() {
        BookSource exist = DbManager.getDaoSession().getBookSourceDao().load(APPCONST.SERVER_SOURCE_TAG);
        return exist != null;
    }

    private static BookSource buildSource() {
        BookSource source = new BookSource();
        // sourceUrl 同时是 BookSource 的主键，用自定义协议头与网页书源区分开
        source.setSourceUrl(APPCONST.SERVER_SOURCE_TAG);
        source.setSourceEName(SOURCE_E_NAME);
        source.setSourceName(APPCONST.SERVER_SOURCE_NAME);
        source.setSourceGroup(SOURCE_GROUP);
        source.setSourceCharset("utf-8");
        source.setSourceComment("自建服务端书库，数据来自 fyreader-service 的 /api/v1 接口");
        source.setSourceHeaders("");
        source.setLoginUrl("");
        source.setLoginCheckJs("");
        source.setConcurrentRate("0");
        source.setLastUpdateTime(System.currentTimeMillis());
        source.setEnable(true);
        // 服务端多在本机或局域网内，走系统代理往往反而连不上
        source.setNoProxy(true);
        source.setWeight(0);
        source.setOrderNum(0);
        // 服务端书源不参与规则解析，但规则对象不能为 null，
        // 否则书源管理界面与部分公共流程会抛空指针
        source.setSearchRule(new SearchRule());
        source.setInfoRule(new InfoRule());
        source.setTocRule(new TocRule());
        source.setContentRule(new ContentRule());
        source.setFindRule(new FindRule());
        return source;
    }
}
