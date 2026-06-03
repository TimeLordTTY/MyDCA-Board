package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.NetWorthSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * NetWorthSnapshotMapper 组件，定义 MyBatis SQL 映射，返回持久化对象或统计视图。
 */
@Mapper
public interface NetWorthSnapshotMapper {
    int upsert(NetWorthSnapshot snapshot);

    NetWorthSnapshot selectByUserAndDate(@Param("userId") Long userId,
                                         @Param("snapshotDate") LocalDate snapshotDate);
}

