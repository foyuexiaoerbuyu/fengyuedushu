package com.fyreader.service.mapper;

import com.fyreader.service.entity.BookGroup;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BookGroupMapper {

    List<BookGroup> selectAll();
}
