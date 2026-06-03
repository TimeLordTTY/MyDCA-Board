package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.IndicatorDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface IndicatorDailyMapper {
    List<IndicatorDaily> selectByProductId(@Param("productId") Long productId, 
                                          @Param("startDate") LocalDate startDate, 
                                          @Param("endDate") LocalDate endDate,
                                          @Param("windowDays") Integer windowDays);
    IndicatorDaily selectLatest(@Param("productId") Long productId, @Param("windowDays") Integer windowDays);
    int insert(IndicatorDaily indicator);
    int update(IndicatorDaily indicator);
}
