package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.Family;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * FamilyMapper 组件，定义 MyBatis SQL 映射，返回持久化对象或统计视图。
 */
@Mapper
public interface FamilyMapper {
    Family selectById(@Param("id") Long id);
    Family selectByCode(@Param("familyCode") String familyCode);
    int insert(Family family);
    int update(Family family);
}

