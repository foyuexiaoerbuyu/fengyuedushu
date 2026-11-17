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

package xyz.fycz.myreader.webapi.crawler.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import xyz.fycz.myreader.common.APPCONST;
import xyz.fycz.myreader.entity.SearchBookBean;
import xyz.fycz.myreader.entity.StrResponse;
import xyz.fycz.myreader.greendao.entity.Book;
import xyz.fycz.myreader.greendao.entity.Chapter;
import xyz.fycz.myreader.model.mulvalmap.ConMVMap;
import xyz.fycz.myreader.webapi.crawler.base.BaseReadCrawler;
import xyz.fycz.myreader.webapi.crawler.base.BookInfoCrawler;
import xyz.fycz.myreader.webapi.crawler.base.ReadCrawler;
import xyz.fycz.myreader.webapi.server.ServerApi;

/**
 * 自建服务端书源。
 * <p>
 * 该类本身不做任何网页抓取与规则解析，只作为「书源身份」存在：
 * [xyz.fycz.myreader.webapi.BookApi] 通过 [isServer] 识别到它之后，
 * 会把请求全部转给 [ServerApi]，改由后端 /api/v1 提供数据。
 * <p>
 * 之所以仍然做成 ReadCrawler，是为了让书源选择、书籍来源标记、分组等既有
 * UI 与数据链路无需改造。
 *
 * @author fengyue
 */
public class ServerCrawler extends BaseReadCrawler implements BookInfoCrawler {

    private static final ServerCrawler INSTANCE = new ServerCrawler();

    private ServerCrawler() {
    }

    public static ServerCrawler getInstance() {
        return INSTANCE;
    }

    /**
     * 判断一个书源是否指向自建服务端。
     */
    public static boolean isServer(ReadCrawler crawler) {
        if (crawler instanceof ServerCrawler) {
            return true;
        }
        return crawler != null && ServerApi.isServerSource(crawler.getNameSpace());
    }

    /**
     * 判断一本书是否来自自建服务端。
     */
    public static boolean isServerBook(Book book) {
        return book != null && ServerApi.isServerSource(book.getSource());
    }

    @Override
    public String getSearchLink() {
        return "";
    }

    @Override
    public String getCharset() {
        return "utf-8";
    }

    @Override
    public String getSearchCharset() {
        return "utf-8";
    }

    @Override
    public String getNameSpace() {
        // 服务端书源没有站点主页，接口返回的封面/章节地址已是绝对 URL；
        // 返回空串让 NetworkUtils.getAbsoluteURL 原样透传，避免 "fyreader://" 非法协议解析异常。
        return "";
    }

    @Override
    public Boolean isPost() {
        return false;
    }

    /**
     * 服务端模式下书籍信息由接口返回，此处不做 HTML 解析。
     */
    @Override
    public Book getBookInfo(String html, Book book) {
        return book;
    }

    /**
     * 服务端模式下不经过 HTML 解析链路，重写为空实现，避免父类抛出 NPE。
     */
    @Override
    public Observable<ConMVMap<SearchBookBean, Book>> getBooksFromStrResponse(StrResponse response) {
        return Observable.just(new ConMVMap<>());
    }

    @Override
    public Observable<List<Chapter>> getChaptersFromStrResponse(StrResponse response) {
        return Observable.just(new ArrayList<Chapter>());
    }

    @Override
    public Observable<String> getContentFormStrResponse(StrResponse response) {
        return Observable.just("");
    }

    @Override
    public ConMVMap<SearchBookBean, Book> getBooksFromSearchHtml(String html) {
        return new ConMVMap<>();
    }

    @Override
    public ArrayList<Chapter> getChaptersFromHtml(String html) {
        return new ArrayList<>(Collections.<Chapter>emptyList());
    }

    @Override
    public String getContentFormHtml(String html) {
        return "";
    }
}
