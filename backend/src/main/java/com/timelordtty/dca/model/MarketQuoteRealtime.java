package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实时行情表实体
 */
public class MarketQuoteRealtime {
    /**
     * 主键 ID，用于数据库内部唯一定位记录。
     */
    private Long id;
    /**
     * 关联产品 ID，指向基金、ETF 或其他投资产品。
     */
    private Long productId;
    /**
     * 时间戳，用于记录业务发生或系统审计时间。
     */
    private LocalDateTime quoteTime;
    /**
     * 成交或行情价格，用于订单、行情或估值展示。
     */
    private BigDecimal price;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal prevClose;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal pctChg;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal volume;
    /**
     * 金额，单位为该账户或流水的币种。
     */
    private BigDecimal amount;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal iopv;
    /**
     * 日期或时间字段，用于业务归属、确认或审计排序。
     */
    private BigDecimal premiumRate;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal openPrice;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal highPrice;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal lowPrice;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private String source;

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
     * 返回时间戳，用于记录业务发生或系统审计时间。
     */
    public LocalDateTime getQuoteTime() {
        return quoteTime;
    }

    /**
     * 设置时间戳，用于记录业务发生或系统审计时间。
     */
    public void setQuoteTime(LocalDateTime quoteTime) {
        this.quoteTime = quoteTime;
    }

    /**
     * 返回成交或行情价格，用于订单、行情或估值展示。
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * 设置成交或行情价格，用于订单、行情或估值展示。
     */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /**
     * 返回行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public BigDecimal getPrevClose() {
        return prevClose;
    }

    /**
     * 设置行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public void setPrevClose(BigDecimal prevClose) {
        this.prevClose = prevClose;
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public BigDecimal getPctChg() {
        return pctChg;
    }

    /**
     * 设置当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public void setPctChg(BigDecimal pctChg) {
        this.pctChg = pctChg;
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public BigDecimal getVolume() {
        return volume;
    }

    /**
     * 设置当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    /**
     * 返回金额，单位为该账户或流水的币种。
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * 设置金额，单位为该账户或流水的币种。
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public BigDecimal getIopv() {
        return iopv;
    }

    /**
     * 设置当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public void setIopv(BigDecimal iopv) {
        this.iopv = iopv;
    }

    /**
     * 返回日期或时间字段，用于业务归属、确认或审计排序。
     */
    public BigDecimal getPremiumRate() {
        return premiumRate;
    }

    /**
     * 设置日期或时间字段，用于业务归属、确认或审计排序。
     */
    public void setPremiumRate(BigDecimal premiumRate) {
        this.premiumRate = premiumRate;
    }

    /**
     * 返回行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    /**
     * 设置行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    /**
     * 返回行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public BigDecimal getHighPrice() {
        return highPrice;
    }

    /**
     * 设置行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    /**
     * 返回行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    /**
     * 设置行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public String getSource() {
        return source;
    }

    /**
     * 设置当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public void setSource(String source) {
        this.source = source;
    }
}
