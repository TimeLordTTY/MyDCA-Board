package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.UserFamilyRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserFamilyRoleMapper {
    List<UserFamilyRole> selectByUserId(@Param("userId") Long userId);
    List<UserFamilyRole> selectByFamilyId(@Param("familyId") Long familyId);
    String selectRole(@Param("userId") Long userId, @Param("familyId") Long familyId);
    int countAdmins(@Param("familyId") Long familyId);
    int updateRole(@Param("userId") Long userId, @Param("familyId") Long familyId, @Param("role") String role);
    int insert(UserFamilyRole userFamilyRole);
    int delete(@Param("userId") Long userId, @Param("familyId") Long familyId);
}

