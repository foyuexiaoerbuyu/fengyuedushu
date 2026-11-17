package com.fyreader.service.mapper;

import com.fyreader.service.entity.BookSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookSourceMapper {

    List<BookSource> selectAll(@Param("onlyEnabled") boolean onlyEnabled);

    BookSource selectById(@Param("id") String id);

    int insert(BookSource source);

    int update(BookSource source);

    int deleteById(@Param("id") String id);
}
