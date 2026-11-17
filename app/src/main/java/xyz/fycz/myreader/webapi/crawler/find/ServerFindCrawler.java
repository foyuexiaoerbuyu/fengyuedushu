/*
 * 本机书库（服务端书源）的发现页入口。
 *
 * 与网页书源不同，本爬虫不请求任何网页：它只负责向发现页暴露一个「分类」，
 * 分类的 url 使用服务端书源标记，实际数据由 BookApi.findBooks 分流到 ServerApi 拉取。
 */
package xyz.fycz.myreader.webapi.crawler.find;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import xyz.fycz.myreader.common.APPCONST;
import xyz.fycz.myreader.entity.FindKind;
import xyz.fycz.myreader.entity.StrResponse;
import xyz.fycz.myreader.greendao.entity.Book;
import xyz.fycz.myreader.webapi.crawler.base.BaseFindCrawler;

public class ServerFindCrawler extends BaseFindCrawler {

    /** 发现页中展示的分组名 */
    public static final String GROUP_NAME = APPCONST.SERVER_SOURCE_NAME;

    @Override
    public String getName() {
        return APPCONST.SERVER_SOURCE_NAME;
    }

    @Override
    public String getTag() {
        return APPCONST.SERVER_SOURCE_NAME;
    }

    @Override
    public Observable<Boolean> initData() {
        return Observable.fromCallable(() -> {
            if (kindsMap.isEmpty()) {
                FindKind kind = new FindKind();
                kind.setTag(GROUP_NAME);
                kind.setName("全部书籍");
                // 服务端标记：BookApi.findBooks 据此分流到 ServerApi.findBooks(page)
                kind.setUrl(APPCONST.SERVER_SOURCE_TAG);
                List<FindKind> kinds = new ArrayList<>();
                kinds.add(kind);
                kindsMap.put(GROUP_NAME, kinds);
            }
            return true;
        });
    }

    @Override
    public Observable<List<Book>> getFindBooks(StrResponse strResponse, FindKind kind) {
        // 服务端书源不解析网页内容，数据由 BookApi 直接分流获取
        return Observable.just(Collections.emptyList());
    }
}
