package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.NetWorthSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface NetWorthSnapshotMapper {
    int upsert(NetWorthSnapshot snapshot);

    NetWorthSnapshot selectByUserAndDate(@Param("userId") Long userId,
                                         @Param("snapshotDate") LocalDate snapshotDate);
}

