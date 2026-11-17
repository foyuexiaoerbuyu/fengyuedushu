/*
 * 本机书库（服务端书源）的发现页入口。
 *
 * 与网页书源不同，本爬虫不请求任何网页：它只负责向发现页暴露一个「分类」，
 * 分类的 url 使用服务端书源标记，实际数据由 BookApi.findBooks 分流到 ServerApi 拉取。
 */
package xyz.fycz.myreader.webapi.crawler.find;

import android.net.Uri;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import xyz.fycz.myreader.common.APPCONST;
import xyz.fycz.myreader.entity.FindKind;
import xyz.fycz.myreader.entity.StrResponse;
import xyz.fycz.myreader.greendao.entity.Book;
import xyz.fycz.myreader.webapi.crawler.base.BaseFindCrawler;
import xyz.fycz.myreader.webapi.server.ServerApi;
import xyz.fycz.myreader.webapi.server.ServerCategory;

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
                List<FindKind> kinds = new ArrayList<>();
                // 首位固定「全部书籍」，不带分类参数
                kinds.add(buildKind("全部书籍", APPCONST.SERVER_SOURCE_TAG));
                // 其余按服务端分类聚合生成，url 携带 category 参数
                for (ServerCategory category : ServerApi.findCategoriesBlocking()) {
                    String name = category.getCategory();
                    if (TextUtils.isEmpty(name)) {
                        continue;
                    }
                    kinds.add(buildKind(name, APPCONST.SERVER_SOURCE_TAG + "?category=" + Uri.encode(name)));
                }
                kindsMap.put(GROUP_NAME, kinds);
            }
            return true;
        });
    }

    private static FindKind buildKind(String name, String url) {
        FindKind kind = new FindKind();
        kind.setTag(GROUP_NAME);
        kind.setName(name);
        kind.setUrl(url);
        return kind;
    }

    @Override
    public Observable<List<Book>> getFindBooks(StrResponse strResponse, FindKind kind) {
        // 服务端书源不解析网页内容，数据由 BookApi 直接分流获取
        return Observable.just(Collections.emptyList());
    }
}
