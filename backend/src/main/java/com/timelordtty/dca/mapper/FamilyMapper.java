package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.Family;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FamilyMapper {
    Family selectById(@Param("id") Long id);
    Family selectByCode(@Param("familyCode") String familyCode);
    int insert(Family family);
    int update(Family family);
}

