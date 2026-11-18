package com.fyreader.service.mapper;

import com.fyreader.service.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    User selectById(@Param("id") String id);

    User selectByUsername(@Param("username") String username);

    int insert(User user);

    int updateStatus(@Param("id") String id, @Param("status") Integer status);

    /** 更新密码摘要（BCrypt），调用方负责编码 */
    int updatePassword(@Param("id") String id, @Param("passwordHash") String passwordHash);

    int updateNickname(@Param("id") String id, @Param("nickname") String nickname);

    int deleteById(@Param("id") String id);

    long countAll();

    long countByRole(@Param("role") String role);

    long countList(@Param("keyword") String keyword);

    List<User> selectList(@Param("keyword") String keyword,
                          @Param("offset") int offset,
                          @Param("size") int size);
}
