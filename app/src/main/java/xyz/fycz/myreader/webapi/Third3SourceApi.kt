package xyz.fycz.myreader.webapi

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.reactivex.Observable
import xyz.fycz.myreader.application.App
import xyz.fycz.myreader.entity.SearchBookBean
import xyz.fycz.myreader.greendao.entity.Book
import xyz.fycz.myreader.greendao.entity.Chapter
import xyz.fycz.myreader.model.mulvalmap.ConMVMap
import xyz.fycz.myreader.model.third3.webBook.WebBook
import xyz.fycz.myreader.webapi.crawler.source.Third3Crawler

/**
 * @author fengyue
 * @date 2022/1/20 11:46
 */
object Third3SourceApi : AndroidViewModel(App.getApplication()) {
    val scope = viewModelScope

    fun searchByT3C(
        key: String,
        rc: Third3Crawler
    ): Observable<ConMVMap<SearchBookBean, Book>> {
        return Observable.create { emitter ->
            WebBook.searchBook(
                scope,
                rc.source,
                key,
                1,
            ).timeout(30000L)
                .onSuccess {
                    emitter.onNext(rc.getBooks(it))
                }.onError {
                    emitter.onError(it)
                }.onFinally {
                    emitter.onComplete()
                }
        }
    }

    fun getBookInfoByT3C(book: Book, rc: Third3Crawler): Observable<Book> {
        return Observable.create { emitter ->
            WebBook.getBookInfo(
                scope,
                rc.source,
                book,
            ).onSuccess {
                emitter.onNext(it)
            }.onError {
                emitter.onError(it)
            }.onFinally {
                emitter.onComplete()
            }
        }
    }

    fun getBookChaptersByT3C(book: Book, rc: Third3Crawler): Observable<List<Chapter>>{
        return Observable.create { emitter ->
            WebBook.getChapterList(
                scope,
                rc.source,
                book,
            ).onSuccess {
                emitter.onNext(it)
            }.onError {
                emitter.onError(it)
            }.onFinally {
                emitter.onComplete()
            }
        }
    }

    fun getChapterContentByT3C(chapter: Chapter, book: Book, rc: Third3Crawler): Observable<String>{
        return Observable.create { emitter ->
            WebBook.getContent(
                scope,
                rc.source,
                book,
                chapter
            ).onSuccess {
                emitter.onNext(it)
            }.onError {
                emitter.onError(it)
            }.onFinally {
                emitter.onComplete()
            }
        }
    }
}