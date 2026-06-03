package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.MarketBarDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MarketBarDailyMapper {
    List<MarketBarDaily> selectByProductId(@Param("productId") Long productId, 
                                           @Param("startDate") LocalDate startDate, 
                                           @Param("endDate") LocalDate endDate);
    MarketBarDaily selectLatest(@Param("productId") Long productId);
    int insert(MarketBarDaily bar);
    int update(MarketBarDaily bar);
}
