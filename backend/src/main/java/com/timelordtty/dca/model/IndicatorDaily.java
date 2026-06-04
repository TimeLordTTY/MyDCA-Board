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
     * 指标回看窗口天数，用于计算百分位、均线或回撤等时间序列指标。
     */
    private Integer windowDays;
    /**
     * 价格或净值在回看窗口中的百分位排名，用于判断当前估值所处区间。
     */
    private BigDecimal pctRank;
    /**
     * 买入分位参考价，来自指标计算结果，用于策略页面展示低位买入区间。
     */
    private BigDecimal qBuyPrice;
    /**
     * 中位分位参考价，来自指标计算结果，用于估值中枢展示。
     */
    private BigDecimal qMidPrice;
    /**
     * 高位分位参考价，来自指标计算结果，用于提示相对高估区间。
     */
    private BigDecimal qHighPrice;
    /**
     * 回看窗口内最高收盘价，用于计算从阶段高点以来的回撤比例。
     */
    private BigDecimal peakClose;
    /**
     * 从阶段高点回撤比例，用于衡量当前价格相对近期高点的下跌幅度。
     */
    private BigDecimal drawdownFromPeak;
    /**
     * 20 日移动平均值，用于短周期趋势判断。
     */
    private BigDecimal ma20;
    /**
     * 60 日移动平均值，用于中周期趋势判断。
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
     * 读取指标计算窗口天数，例如百分位或均线统计使用的回看区间。
     */
    public Integer getWindowDays() {
        return windowDays;
    }

    /**
     * 设置指标回看窗口天数，决定百分位和均线等计算使用的历史范围。
     */
    public void setWindowDays(Integer windowDays) {
        this.windowDays = windowDays;
    }

    /**
     * 读取当前指标值在回看窗口中的百分位排名。
     */
    public BigDecimal getPctRank() {
        return pctRank;
    }

    /**
     * 设置百分位排名，保存指标计算得到的估值位置。
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
     * 读取从阶段高点回撤的比例，用于衡量标的下跌幅度。
     */
    public BigDecimal getDrawdownFromPeak() {
        return drawdownFromPeak;
    }

    /**
     * 设置阶段回撤比例，保存当前价格相对窗口高点的下跌幅度。
     */
    public void setDrawdownFromPeak(BigDecimal drawdownFromPeak) {
        this.drawdownFromPeak = drawdownFromPeak;
    }

    /**
     * 读取 20 日移动平均值，用于短周期趋势判断。
     */
    public BigDecimal getMa20() {
        return ma20;
    }

    /**
     * 设置 20 日移动平均值，保存短周期趋势指标。
     */
    public void setMa20(BigDecimal ma20) {
        this.ma20 = ma20;
    }

    /**
     * 读取 60 日移动平均值，用于中周期趋势判断。
     */
    public BigDecimal getMa60() {
        return ma60;
    }

    /**
     * 设置 60 日移动平均值，保存中周期趋势指标。
     */
    public void setMa60(BigDecimal ma60) {
        this.ma60 = ma60;
    }
}
