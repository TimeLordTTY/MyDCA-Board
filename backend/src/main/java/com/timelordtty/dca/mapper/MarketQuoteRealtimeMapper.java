package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.MarketQuoteRealtime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MarketQuoteRealtimeMapper {
    List<MarketQuoteRealtime> selectByProductIds(@Param("productIds") List<Long> productIds);
    MarketQuoteRealtime selectLatest(@Param("productId") Long productId);
    List<MarketQuoteRealtime> selectHistory(@Param("productId") Long productId,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);
    int insert(MarketQuoteRealtime quote);
    int update(MarketQuoteRealtime quote);
}
