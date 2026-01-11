package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日更指标表实体
 */
public class IndicatorDaily {
    private Long id;
    private Long productId;
    private LocalDate tradeDate;
    private Integer windowDays;
    private BigDecimal pctRank;
    private BigDecimal qBuyPrice;
    private BigDecimal qMidPrice;
    private BigDecimal qHighPrice;
    private BigDecimal peakClose;
    private BigDecimal drawdownFromPeak;
    private BigDecimal ma20;
    private BigDecimal ma60;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public Integer getWindowDays() {
        return windowDays;
    }

    public void setWindowDays(Integer windowDays) {
        this.windowDays = windowDays;
    }

    public BigDecimal getPctRank() {
        return pctRank;
    }

    public void setPctRank(BigDecimal pctRank) {
        this.pctRank = pctRank;
    }

    public BigDecimal getQBuyPrice() {
        return qBuyPrice;
    }

    public void setQBuyPrice(BigDecimal qBuyPrice) {
        this.qBuyPrice = qBuyPrice;
    }

    public BigDecimal getQMidPrice() {
        return qMidPrice;
    }

    public void setQMidPrice(BigDecimal qMidPrice) {
        this.qMidPrice = qMidPrice;
    }

    public BigDecimal getQHighPrice() {
        return qHighPrice;
    }

    public void setQHighPrice(BigDecimal qHighPrice) {
        this.qHighPrice = qHighPrice;
    }

    public BigDecimal getPeakClose() {
        return peakClose;
    }

    public void setPeakClose(BigDecimal peakClose) {
        this.peakClose = peakClose;
    }

    public BigDecimal getDrawdownFromPeak() {
        return drawdownFromPeak;
    }

    public void setDrawdownFromPeak(BigDecimal drawdownFromPeak) {
        this.drawdownFromPeak = drawdownFromPeak;
    }

    public BigDecimal getMa20() {
        return ma20;
    }

    public void setMa20(BigDecimal ma20) {
        this.ma20 = ma20;
    }

    public BigDecimal getMa60() {
        return ma60;
    }

    public void setMa60(BigDecimal ma60) {
        this.ma60 = ma60;
    }
}
