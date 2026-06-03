package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.HoldingsSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * HoldingsSnapshotMapper 组件，定义 MyBatis SQL 映射，返回持久化对象或统计视图。
 */
@Mapper
public interface HoldingsSnapshotMapper {
    int upsert(HoldingsSnapshot snapshot);

    List<HoldingsSnapshot> selectByUserAndDate(@Param("userId") Long userId,
                                               @Param("snapshotDate") LocalDate snapshotDate);
}

