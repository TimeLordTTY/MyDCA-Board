package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日更指标表实体
 */
public class IndicatorDaily {
    /**
     * 主键 ID，用于数据库内部唯一定位记录。
     */
    private Long id;
    /**
     * 关联产品 ID，指向基金、ETF 或其他投资产品。
     */
    private Long productId;
    /**
     * 业务日期，用于交易归属、确认或统计周期判定。
     */
    private LocalDate tradeDate;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private Integer windowDays;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal pctRank;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal qBuyPrice;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal qMidPrice;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal qHighPrice;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal peakClose;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal drawdownFromPeak;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal ma20;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal ma60;

    // Getters and Setters
    /**
     * 返回主键 ID，用于数据库内部唯一定位记录。
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置主键 ID，用于数据库内部唯一定位记录。
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 返回关联产品 ID，指向基金、ETF 或其他投资产品。
     */
    public Long getProductId() {
        return productId;
    }

    /**
     * 设置关联产品 ID，指向基金、ETF 或其他投资产品。
     */
    public void setProductId(Long productId) {
        this.productId = productId;
    }

    /**
     * 返回业务日期，用于交易归属、确认或统计周期判定。
     */
    public LocalDate getTradeDate() {
        return tradeDate;
    }

    /**
     * 设置业务日期，用于交易归属、确认或统计周期判定。
     */
    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public Integer getWindowDays() {
        return windowDays;
    }

    /**
     * 设置当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public void setWindowDays(Integer windowDays) {
        this.windowDays = windowDays;
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public BigDecimal getPctRank() {
        return pctRank;
    }

    /**
     * 设置当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public void setPctRank(BigDecimal pctRank) {
        this.pctRank = pctRank;
    }

    /**
     * 返回行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public BigDecimal getQBuyPrice() {
        return qBuyPrice;
    }

    /**
     * 设置行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public void setQBuyPrice(BigDecimal qBuyPrice) {
        this.qBuyPrice = qBuyPrice;
    }

    /**
     * 返回行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public BigDecimal getQMidPrice() {
        return qMidPrice;
    }

    /**
     * 设置行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public void setQMidPrice(BigDecimal qMidPrice) {
        this.qMidPrice = qMidPrice;
    }

    /**
     * 返回行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public BigDecimal getQHighPrice() {
        return qHighPrice;
    }

    /**
     * 设置行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public void setQHighPrice(BigDecimal qHighPrice) {
        this.qHighPrice = qHighPrice;
    }

    /**
     * 返回行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public BigDecimal getPeakClose() {
        return peakClose;
    }

    /**
     * 设置行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public void setPeakClose(BigDecimal peakClose) {
        this.peakClose = peakClose;
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public BigDecimal getDrawdownFromPeak() {
        return drawdownFromPeak;
    }

    /**
     * 设置当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public void setDrawdownFromPeak(BigDecimal drawdownFromPeak) {
        this.drawdownFromPeak = drawdownFromPeak;
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public BigDecimal getMa20() {
        return ma20;
    }

    /**
     * 设置当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public void setMa20(BigDecimal ma20) {
        this.ma20 = ma20;
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public BigDecimal getMa60() {
        return ma60;
    }

    /**
     * 设置当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public void setMa60(BigDecimal ma60) {
        this.ma60 = ma60;
    }
}
