package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.MarketBarDailyMapper;
import com.timelordtty.dca.mapper.MarketQuoteRealtimeMapper;
import com.timelordtty.dca.model.MarketBarDaily;
import com.timelordtty.dca.model.MarketQuoteRealtime;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 行情服务
 */
@Service
public class MarketService {

    private final MarketBarDailyMapper marketBarDailyMapper;
    private final MarketQuoteRealtimeMapper marketQuoteRealtimeMapper;

    public MarketService(MarketBarDailyMapper marketBarDailyMapper, 
                        MarketQuoteRealtimeMapper marketQuoteRealtimeMapper) {
        this.marketBarDailyMapper = marketBarDailyMapper;
        this.marketQuoteRealtimeMapper = marketQuoteRealtimeMapper;
    }

    /**
     * 获取历史行情（日K线）
     */
    public List<MarketBarDaily> getHistoryBars(Long productId, LocalDate startDate, LocalDate endDate) {
        return marketBarDailyMapper.selectByProductId(productId, startDate, endDate);
    }

    /**
     * 获取最新日K线
     */
    public MarketBarDaily getLatestBar(Long productId) {
        return marketBarDailyMapper.selectLatest(productId);
    }

    /**
     * 获取实时行情
     */
    public List<MarketQuoteRealtime> getRealtimeQuotes(List<Long> productIds) {
        return marketQuoteRealtimeMapper.selectByProductIds(productIds);
    }

    /**
     * 获取单个产品的最新实时行情
     */
    public MarketQuoteRealtime getLatestQuote(Long productId) {
        return marketQuoteRealtimeMapper.selectLatest(productId);
    }
}
