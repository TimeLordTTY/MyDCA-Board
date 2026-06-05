package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.IndicatorDailyMapper;
import com.timelordtty.dca.model.IndicatorDaily;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 指标服务
 */
@Service
public class IndicatorService {

    /**
     * 日指标 Mapper，负责技术指标结果的按标的和日期持久化访问。
     */
    private final IndicatorDailyMapper indicatorDailyMapper;
    /**
     * 行情服务入口，负责行情、K 线和实时价格数据的查询与刷新。
     */
    private final MarketService marketService;

    /**
     * 装配指标和行情 Mapper，用于保存技术指标并读取指标计算所需的行情序列。
     */
    public IndicatorService(IndicatorDailyMapper indicatorDailyMapper, MarketService marketService) {
        this.indicatorDailyMapper = indicatorDailyMapper;
        this.marketService = marketService;
    }

    /**
     * 获取历史指标数据
     */
    public List<IndicatorDaily> getHistoryIndicators(Long productId, LocalDate startDate, LocalDate endDate, Integer windowDays) {
        List<IndicatorDaily> indicators = indicatorDailyMapper.selectByProductId(productId, startDate, endDate, windowDays);
        if (indicators != null && !indicators.isEmpty()) {
            return indicators;
        }

        // 兜底：若指标表为空（常见于OTC基金），基于“日K/净值派生K线”临时计算前端指标图所需字段。
        // 该路径只做内存派生，不写入 indicator_daily，也不连接生产数据库。
        List<com.timelordtty.dca.model.MarketBarDaily> bars = marketService.getHistoryBars(productId, startDate, endDate);
        if (bars == null || bars.isEmpty()) {
            return List.of();
        }

        // bars 默认倒序，这里转为正序便于滚动窗口计算
        List<com.timelordtty.dca.model.MarketBarDaily> asc = new ArrayList<>(bars);
        asc.sort(Comparator.comparing(com.timelordtty.dca.model.MarketBarDaily::getTradeDate));

        List<BigDecimal> closes = asc.stream()
                .map(b -> b.getClosePrice() != null ? b.getClosePrice() : BigDecimal.ZERO)
                .toList();
        List<IndicatorMath.KdjBar> kdjBars = asc.stream()
                .map(b -> new IndicatorMath.KdjBar(
                        b.getHighPrice() != null ? b.getHighPrice() : b.getClosePrice(),
                        b.getLowPrice() != null ? b.getLowPrice() : b.getClosePrice(),
                        b.getClosePrice()))
                .toList();

        List<IndicatorDaily> derived = new ArrayList<>(asc.size());
        BigDecimal previousK = null;
        BigDecimal previousD = null;
        for (int i = 0; i < asc.size(); i++) {
            IndicatorDaily d = new IndicatorDaily();
            d.setProductId(productId);
            d.setTradeDate(asc.get(i).getTradeDate());
            d.setWindowDays(windowDays);

            // MA20/MA60（若不足窗口则置null）
            d.setMa20(calcMA(closes, i, 20));
            d.setMa60(calcMA(closes, i, 60));

            int bollWindow = normalizeWindow(windowDays, 20);
            IndicatorMath.BollValue boll = IndicatorMath.calculateBoll(closes, i, bollWindow);
            if (boll != null) {
                d.setBollMiddle(boll.middle());
                d.setBollUpper(boll.upper());
                d.setBollLower(boll.lower());
                d.setBollStd(boll.std());
                d.setBollWindow(bollWindow);
            }

            IndicatorMath.KdjValue kdj = IndicatorMath.calculateKdj(kdjBars, i, 9, previousK, previousD);
            if (kdj != null) {
                d.setKdjK(kdj.k());
                d.setKdjD(kdj.d());
                d.setKdjJ(kdj.j());
                d.setKdjRsv(kdj.rsv());
                d.setKdjWindow(9);
                previousK = kdj.k();
                previousD = kdj.d();
            }

            // 分位：以 windowDays 作为滚动窗口，计算当前 close 在窗口内的经验分位 [0,1]
            d.setPctRank(calcPctRank(closes, i, windowDays));

            derived.add(d);
        }

        // 对外保持倒序
        derived.sort(Comparator.comparing(IndicatorDaily::getTradeDate).reversed());
        return derived;
    }

    /**
     * 获取最新指标数据
     */
    public IndicatorDaily getLatestIndicator(Long productId, Integer windowDays) {
        IndicatorDaily latest = indicatorDailyMapper.selectLatest(productId, windowDays);
        if (latest != null) {
            return latest;
        }
        // 兜底：尝试从最近90天临时计算
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(90);
        List<IndicatorDaily> list = getHistoryIndicators(productId, start, end, windowDays);
        return list.isEmpty() ? null : list.get(0); // list 已按倒序
    }

    private int normalizeWindow(Integer windowDays, int defaultWindow) {
        return windowDays != null && windowDays > 0 ? windowDays : defaultWindow;
    }

    private BigDecimal calcMA(List<BigDecimal> closes, int idx, int window) {
        if (idx + 1 < window) return null;
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = idx - window + 1; i <= idx; i++) {
            sum = sum.add(closes.get(i));
        }
        return sum.divide(BigDecimal.valueOf(window), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal calcPctRank(List<BigDecimal> closes, int idx, Integer windowDays) {
        int w = (windowDays != null && windowDays > 0) ? windowDays : 20;
        int start = Math.max(0, idx - w + 1);
        List<BigDecimal> window = closes.subList(start, idx + 1);
        if (window.isEmpty()) return null;
        BigDecimal current = closes.get(idx);
        long le = window.stream().filter(v -> v.compareTo(current) <= 0).count();
        return BigDecimal.valueOf(le)
                .divide(BigDecimal.valueOf(window.size()), 6, RoundingMode.HALF_UP);
    }
}
