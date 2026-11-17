package com.fyreader.service.mapper;

import com.fyreader.service.entity.Chapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChapterMapper {

    Chapter selectById(@Param("id") String id);

    Chapter selectByBookAndNo(@Param("bookId") String bookId,
                              @Param("chapterNo") Integer chapterNo);

    /** withContent = false 时不查询 content 列，节省带宽 */
    List<Chapter> selectByBookId(@Param("bookId") String bookId,
                                 @Param("withContent") boolean withContent,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    long countByBookId(@Param("bookId") String bookId);

    /** 全部章节数，用于站点统计 */
    long countAll();

    /** 当前最大章节号，无章节时返回 null */
    Integer selectMaxChapterNo(@Param("bookId") String bookId);

    /** 取下一章 / 上一章 */
    Chapter selectNeighbor(@Param("bookId") String bookId,
                           @Param("chapterNo") Integer chapterNo,
                           @Param("direction") String direction);

    int insert(Chapter chapter);

    int update(Chapter chapter);

    int deleteById(@Param("id") String id);

    int deleteByBookId(@Param("bookId") String bookId);

    int updateContent(@Param("id") String id,
                      @Param("content") String content,
                      @Param("wordCount") Integer wordCount);
}
