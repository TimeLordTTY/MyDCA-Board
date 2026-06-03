package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.MarketQuoteRealtime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MarketQuoteRealtimeMapper 组件，定义 MyBatis SQL 映射，返回持久化对象或统计视图。
 */
@Mapper
public interface MarketQuoteRealtimeMapper {
    List<MarketQuoteRealtime> selectByProductIds(@Param("productIds") List<Long> productIds);
    MarketQuoteRealtime selectLatest(@Param("productId") Long productId);
    List<MarketQuoteRealtime> selectHistory(@Param("productId") Long productId,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);
    int deleteByQuoteTimeBefore(@Param("before") LocalDateTime before);
    int insert(MarketQuoteRealtime quote);
    int update(MarketQuoteRealtime quote);
}
