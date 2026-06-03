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
/**
 * 业务注释规范化: MarketService 服务类，负责业务规则、账户、账本流水、订单或持仓数据的组合处理。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class MarketService {

    /**
     * 业务注释规范化: marketBarDailyMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final MarketBarDailyMapper marketBarDailyMapper;
    /**
     * 业务注释规范化: marketQuoteRealtimeMapper 时间字段，用于记录业务动作发生或审计时间。
     */
    private final MarketQuoteRealtimeMapper marketQuoteRealtimeMapper;
    /**
     * 业务注释规范化: navMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final NavMapper navMapper;

    /**
     * 业务注释规范化: 处理 MarketService 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param marketBarDailyMapper marketBarDailyMapper 业务字段，承载该对象在后端流程中的核心属性。
     * @param marketQuoteRealtimeMapper marketQuoteRealtimeMapper 时间字段，用于记录业务动作发生或审计时间。
     * @param navMapper navMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
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
    /**
     * 业务注释规范化: 查询 getHistoryBars 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @param startDate startDate 日期字段，用于交易、确认、净值或统计周期口径。
     * @param endDate endDate 日期字段，用于交易、确认、净值或统计周期口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
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
    /**
     * 业务注释规范化: 查询 getLatestBar 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
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
    /**
     * 业务注释规范化: 查询 getRealtimeQuotes 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productIds productIds 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public List<MarketQuoteRealtime> getRealtimeQuotes(List<Long> productIds) {
        return marketQuoteRealtimeMapper.selectByProductIds(productIds);
    }

    /**
     * 获取单个产品的最新实时行情
     */
    /**
     * 业务注释规范化: 查询 getLatestQuote 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public MarketQuoteRealtime getLatestQuote(Long productId) {
        return marketQuoteRealtimeMapper.selectLatest(productId);
    }

    /**
     * 获取实时行情历史（用于IOPV/估值曲线）
     */
    /**
     * 业务注释规范化: 查询 getQuoteHistory 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @param startTime startTime 时间字段，用于记录业务动作发生或审计时间。
     * @param endTime endTime 时间字段，用于记录业务动作发生或审计时间。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public List<MarketQuoteRealtime> getQuoteHistory(Long productId, LocalDateTime startTime, LocalDateTime endTime) {
        return marketQuoteRealtimeMapper.selectHistory(productId, startTime, endTime);
    }
}
