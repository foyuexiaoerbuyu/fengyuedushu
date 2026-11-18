package com.fyreader.service.mapper;

import com.fyreader.service.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User selectById(@Param("id") String id);

    User selectByUsername(@Param("username") String username);

    int insert(User user);

    int updateStatus(@Param("id") String id, @Param("status") Integer status);

    long countAll();
}
