package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日线行情表实体
 */
public class MarketBarDaily {
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
     * 交易日收盘价，用于净值、指标和收益率计算。
     */
    private BigDecimal closePrice;
    /**
     * 成交量，记录当前行情周期内的成交规模。
     */
    private BigDecimal volume;
    /**
     * 金额，单位为该账户或流水的币种。
     */
    private BigDecimal amount;
    /**
     * 前一交易日收盘价，用于计算涨跌幅和价格变动。
     */
    private BigDecimal prevClose;
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
     * 返回行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public BigDecimal getClosePrice() {
        return closePrice;
    }

    /**
     * 设置行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
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
