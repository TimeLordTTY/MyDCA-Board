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
     * 前一交易日收盘价，用于计算涨跌幅和价格变动。
     */
    private BigDecimal prevClose;
    /**
     * 实时涨跌幅，表示当前价相对前收价的百分比变化。
     */
    private BigDecimal pctChg;
    /**
     * 成交量，记录当前行情周期内的成交规模。
     */
    private BigDecimal volume;
    /**
     * 金额，单位为该账户或流水的币种。
     */
    private BigDecimal amount;
    /**
     * ETF 实时参考净值 IOPV，用于计算场内交易溢价率。
     */
    private BigDecimal iopv;
    /**
     * 实时行情溢价率，表示交易价格相对 IOPV 或参考净值的偏离比例。
     */
    private BigDecimal premiumRate;
    /**
     * 交易日开盘价，用于 K 线展示和涨跌幅计算。
     */
    private BigDecimal openPrice;
    /**
     * 交易日最高价，用于 K 线展示和波动区间计算。
     */
    private BigDecimal highPrice;
    /**
     * 交易日最低价，用于 K 线展示和波动区间计算。
     */
    private BigDecimal lowPrice;
    /**
     * 数据来源标识，用于区分脚本采集、人工导入或第三方行情来源。
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
     * 读取实时涨跌幅，表示当前价相对前收价的百分比变化。
     */
    public BigDecimal getPctChg() {
        return pctChg;
    }

    /**
     * 设置实时涨跌幅，保存当前价相对前收价的变化比例。
     */
    public void setPctChg(BigDecimal pctChg) {
        this.pctChg = pctChg;
    }

    /**
     * 读取成交量字段，表示当前行情周期内的成交规模。
     */
    public BigDecimal getVolume() {
        return volume;
    }

    /**
     * 设置成交量，保存行情源返回的成交规模。
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
     * 读取 ETF 实时参考净值 IOPV，用于计算场内溢价率。
     */
    public BigDecimal getIopv() {
        return iopv;
    }

    /**
     * 设置 ETF 实时参考净值，用于后续计算溢价率。
     */
    public void setIopv(BigDecimal iopv) {
        this.iopv = iopv;
    }

    /**
     * 读取场内溢价率，表示交易价格相对参考净值的偏离比例。
     */
    public BigDecimal getPremiumRate() {
        return premiumRate;
    }

    /**
     * 设置场内溢价率，保存行情刷新时计算出的价格偏离比例。
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
     * 读取数据来源标识，用于区分脚本、手工导入或第三方行情来源。
     */
    public String getSource() {
        return source;
    }

    /**
     * 设置数据来源标识，记录本条行情或净值来自脚本、导入或外部源。
     */
    public void setSource(String source) {
        this.source = source;
    }
}
