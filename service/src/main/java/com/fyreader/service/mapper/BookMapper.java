package com.fyreader.service.mapper;

import com.fyreader.service.dto.BookQuery;
import com.fyreader.service.entity.Book;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface BookMapper {

    Book selectById(@Param("id") String id);

    /** 按书名精确查询（TXT 重导时复用原书籍 ID） */
    Book selectByName(@Param("name") String name);

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

    /**
     * 按分类聚合书籍数量（发现页分类 Tab 与后台分类筛选使用）。
     *
     * @param viewerId 查看者用户 ID，可为空
     * @param enforce  是否按可见性过滤（公开书 + viewerId 名下私有书）
     */
    List<Map<String, Object>> selectCategoryStats(@Param("viewerId") String viewerId,
                                                  @Param("enforce") boolean enforce);

    /** 同归属用户下的同名书籍（用户重复上传同一本书时复用原书籍 ID） */
    Book selectByNameAndOwner(@Param("name") String name, @Param("ownerId") String ownerId);

    /** 某个用户名下的全部书籍（含私有），keyword 可按书名/作者模糊过滤 */
    List<Book> selectByOwner(@Param("ownerId") String ownerId,
                             @Param("keyword") String keyword,
                             @Param("offset") int offset,
                             @Param("limit") int limit);

    long countByOwner(@Param("ownerId") String ownerId, @Param("keyword") String keyword);

    /** 切换可见性（PUBLIC / PRIVATE） */
    int updateVisibility(@Param("id") String id, @Param("visibility") String visibility);
}
