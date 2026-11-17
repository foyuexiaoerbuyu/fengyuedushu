package com.fyreader.service.mapper;

import com.fyreader.service.dto.BookQuery;
import com.fyreader.service.entity.Book;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookMapper {

    Book selectById(@Param("id") String id);

    List<Book> selectByQuery(@Param("q") BookQuery query,
                             @Param("orderBy") String orderBy,
                             @Param("offset") int offset,
                             @Param("limit") int limit);

    long countByQuery(@Param("q") BookQuery query);

    int insert(Book book);

    int update(Book book);

    int deleteById(@Param("id") String id);

    /** 依据章节表回写章节总数与最新章节 */
    int refreshChapterStat(@Param("bookId") String bookId);

    List<Book> selectAll();
}
