package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.MarketBarDailyMapper;
import com.timelordtty.dca.mapper.MarketQuoteRealtimeMapper;
import com.timelordtty.dca.mapper.NavMapper;
import com.timelordtty.dca.model.MarketBarDaily;
import com.timelordtty.dca.model.MarketQuoteRealtime;
import com.timelordtty.dca.model.Nav;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 行情服务
 */
@Service
public class MarketService {

    private final MarketBarDailyMapper marketBarDailyMapper;
    private final MarketQuoteRealtimeMapper marketQuoteRealtimeMapper;
    private final NavMapper navMapper;

    public MarketService(MarketBarDailyMapper marketBarDailyMapper, 
                        MarketQuoteRealtimeMapper marketQuoteRealtimeMapper,
                        NavMapper navMapper) {
        this.marketBarDailyMapper = marketBarDailyMapper;
        this.marketQuoteRealtimeMapper = marketQuoteRealtimeMapper;
        this.navMapper = navMapper;
    }

    /**
     * 获取历史行情（日K线）
     */
    public List<MarketBarDaily> getHistoryBars(Long productId, LocalDate startDate, LocalDate endDate) {
        List<MarketBarDaily> bars = marketBarDailyMapper.selectByProductId(productId, startDate, endDate);
        if (bars != null && !bars.isEmpty()) {
            return bars;
        }

        // 兜底：若没有日K线数据（例如OTC基金），使用净值序列派生“类K线”
        List<Nav> navs = navMapper.selectByProductId(productId, startDate, endDate);
        if (navs == null || navs.isEmpty()) {
            return List.of();
        }
        // nav 默认是按日期倒序
        List<MarketBarDaily> derived = new ArrayList<>(navs.size());
        BigDecimal prev = null;
        for (int i = navs.size() - 1; i >= 0; i--) { // 转为正序计算 prevClose
            Nav n = navs.get(i);
            BigDecimal close = n.getNav();
            MarketBarDaily b = new MarketBarDaily();
            b.setProductId(productId);
            b.setTradeDate(n.getNavDate());
            b.setOpenPrice(close);
            b.setHighPrice(close);
            b.setLowPrice(close);
            b.setClosePrice(close);
            b.setVolume(BigDecimal.ZERO);
            b.setAmount(BigDecimal.ZERO);
            b.setPrevClose(prev);
            b.setSource("NAV_DERIVED");
            derived.add(b);
            prev = close;
        }
        // 对外保持与 mapper 一致的倒序
        java.util.Collections.reverse(derived);
        return derived;
    }

    /**
     * 获取最新日K线
     */
    public MarketBarDaily getLatestBar(Long productId) {
        MarketBarDaily latest = marketBarDailyMapper.selectLatest(productId);
        if (latest != null) {
            return latest;
        }
        // 兜底：无日K时，用最新净值派生
        Nav nav = navMapper.selectLatest(productId);
        if (nav == null || nav.getNav() == null) {
            return null;
        }
        MarketBarDaily b = new MarketBarDaily();
        b.setProductId(productId);
        b.setTradeDate(nav.getNavDate());
        b.setOpenPrice(nav.getNav());
        b.setHighPrice(nav.getNav());
        b.setLowPrice(nav.getNav());
        b.setClosePrice(nav.getNav());
        b.setVolume(BigDecimal.ZERO);
        b.setAmount(BigDecimal.ZERO);
        b.setPrevClose(null);
        b.setSource("NAV_DERIVED");
        return b;
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

    /**
     * 获取实时行情历史（用于IOPV/估值曲线）
     */
    public List<MarketQuoteRealtime> getQuoteHistory(Long productId, LocalDateTime startTime, LocalDateTime endTime) {
        return marketQuoteRealtimeMapper.selectHistory(productId, startTime, endTime);
    }
}
