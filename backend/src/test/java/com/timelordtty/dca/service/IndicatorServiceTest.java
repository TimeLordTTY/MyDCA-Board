package com.timelordtty.dca.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.timelordtty.dca.mapper.IndicatorDailyMapper;
import com.timelordtty.dca.model.IndicatorDaily;
import com.timelordtty.dca.model.MarketBarDaily;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证指标服务的兜底派生路径，不启动 Spring，也不连接真实数据库。
 */
class IndicatorServiceTest {

    @Test
    void getHistoryIndicatorsDerivesBollAndKdjWhenIndicatorTableIsEmpty() {
        IndicatorDailyMapper indicatorDailyMapper = mock(IndicatorDailyMapper.class);
        MarketService marketService = mock(MarketService.class);
        IndicatorService indicatorService = new IndicatorService(indicatorDailyMapper, marketService);
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = startDate.plusDays(19);
        List<MarketBarDaily> bars = buildAscendingBars(startDate, 20);
        List<MarketBarDaily> descendingBars = new ArrayList<>(bars);
        Collections.reverse(descendingBars);

        when(indicatorDailyMapper.selectByProductId(1L, startDate, endDate, 20)).thenReturn(List.of());
        when(marketService.getHistoryBars(1L, startDate, endDate)).thenReturn(descendingBars);

        List<IndicatorDaily> indicators = indicatorService.getHistoryIndicators(1L, startDate, endDate, 20);

        IndicatorDaily latest = indicators.get(0);
        assertEquals(endDate, latest.getTradeDate());
        assertEquals("10.500000", latest.getBollMiddle().toPlainString());
        assertEquals("5.766281", latest.getBollStd().toPlainString());
        assertEquals(20, latest.getBollWindow());
        assertNotNull(latest.getKdjK());
        assertNotNull(latest.getKdjD());
        assertNotNull(latest.getKdjJ());
        assertEquals("90.000000", latest.getKdjRsv().toPlainString());
        assertEquals(9, latest.getKdjWindow());
    }

    private List<MarketBarDaily> buildAscendingBars(LocalDate startDate, int count) {
        List<MarketBarDaily> bars = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BigDecimal close = BigDecimal.valueOf(i + 1L);
            MarketBarDaily bar = new MarketBarDaily();
            bar.setTradeDate(startDate.plusDays(i));
            bar.setHighPrice(close.add(BigDecimal.ONE));
            bar.setLowPrice(close.subtract(BigDecimal.ONE));
            bar.setClosePrice(close);
            bars.add(bar);
        }
        return bars;
    }
}
