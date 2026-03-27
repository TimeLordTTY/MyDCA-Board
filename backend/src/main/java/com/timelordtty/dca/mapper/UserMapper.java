package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMapper {
    User selectById(@Param("id") Long id);
    User selectByUsername(@Param("username") String username);
    List<Long> selectActiveUserIds();
    int insert(User user);
    int update(User user);
    int updateProfile(@Param("id") Long id,
                      @Param("nickname") String nickname,
                      @Param("email") String email,
                      @Param("phone") String phone);
    int updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash);
    int updateLastLoginAt(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt);
}

