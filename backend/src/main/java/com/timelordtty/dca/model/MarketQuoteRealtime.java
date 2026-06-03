package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实时行情表实体
 */
public class MarketQuoteRealtime {
    private Long id;
    private Long productId;
    private LocalDateTime quoteTime;
    private BigDecimal price;
    private BigDecimal prevClose;
    private BigDecimal pctChg;
    private BigDecimal volume;
    private BigDecimal amount;
    private BigDecimal iopv;
    private BigDecimal premiumRate;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private String source;

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

    public LocalDateTime getQuoteTime() {
        return quoteTime;
    }

    public void setQuoteTime(LocalDateTime quoteTime) {
        this.quoteTime = quoteTime;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getPrevClose() {
        return prevClose;
    }

    public void setPrevClose(BigDecimal prevClose) {
        this.prevClose = prevClose;
    }

    public BigDecimal getPctChg() {
        return pctChg;
    }

    public void setPctChg(BigDecimal pctChg) {
        this.pctChg = pctChg;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getIopv() {
        return iopv;
    }

    public void setIopv(BigDecimal iopv) {
        this.iopv = iopv;
    }

    public BigDecimal getPremiumRate() {
        return premiumRate;
    }

    public void setPremiumRate(BigDecimal premiumRate) {
        this.premiumRate = premiumRate;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
