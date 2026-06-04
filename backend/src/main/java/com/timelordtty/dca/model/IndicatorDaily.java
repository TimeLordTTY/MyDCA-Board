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
    /**
     * BOLL 中轨，通常为窗口内收盘价移动平均值。
     */
    private BigDecimal bollMiddle;
    /**
     * BOLL 上轨，通常为中轨加两倍标准差。
     */
    private BigDecimal bollUpper;
    /**
     * BOLL 下轨，通常为中轨减两倍标准差。
     */
    private BigDecimal bollLower;
    /**
     * BOLL 标准差，衡量窗口内价格波动幅度。
     */
    private BigDecimal bollStd;
    /**
     * BOLL 计算窗口天数，首版默认与 windowDays 保持一致。
     */
    private Integer bollWindow;
    /**
     * KDJ 的 K 值，反映短期价格位置变化。
     */
    private BigDecimal kdjK;
    /**
     * KDJ 的 D 值，是 K 值的平滑结果。
     */
    private BigDecimal kdjD;
    /**
     * KDJ 的 J 值，用于放大 K 与 D 的背离。
     */
    private BigDecimal kdjJ;
    /**
     * KDJ 的 RSV 值，表示当前收盘价在窗口高低区间中的位置。
     */
    private BigDecimal kdjRsv;
    /**
     * KDJ 计算窗口天数，首版默认使用 9 日。
     */
    private Integer kdjWindow;

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
    /**
     * 读取 BOLL 中轨。
     */
    public BigDecimal getBollMiddle() {
        return bollMiddle;
    }

    /**
     * 设置 BOLL 中轨。
     */
    public void setBollMiddle(BigDecimal bollMiddle) {
        this.bollMiddle = bollMiddle;
    }

    /**
     * 读取 BOLL 上轨。
     */
    public BigDecimal getBollUpper() {
        return bollUpper;
    }

    /**
     * 设置 BOLL 上轨。
     */
    public void setBollUpper(BigDecimal bollUpper) {
        this.bollUpper = bollUpper;
    }

    /**
     * 读取 BOLL 下轨。
     */
    public BigDecimal getBollLower() {
        return bollLower;
    }

    /**
     * 设置 BOLL 下轨。
     */
    public void setBollLower(BigDecimal bollLower) {
        this.bollLower = bollLower;
    }

    /**
     * 读取 BOLL 标准差。
     */
    public BigDecimal getBollStd() {
        return bollStd;
    }

    /**
     * 设置 BOLL 标准差。
     */
    public void setBollStd(BigDecimal bollStd) {
        this.bollStd = bollStd;
    }

    /**
     * 读取 BOLL 窗口天数。
     */
    public Integer getBollWindow() {
        return bollWindow;
    }

    /**
     * 设置 BOLL 窗口天数。
     */
    public void setBollWindow(Integer bollWindow) {
        this.bollWindow = bollWindow;
    }

    /**
     * 读取 KDJ 的 K 值。
     */
    public BigDecimal getKdjK() {
        return kdjK;
    }

    /**
     * 设置 KDJ 的 K 值。
     */
    public void setKdjK(BigDecimal kdjK) {
        this.kdjK = kdjK;
    }

    /**
     * 读取 KDJ 的 D 值。
     */
    public BigDecimal getKdjD() {
        return kdjD;
    }

    /**
     * 设置 KDJ 的 D 值。
     */
    public void setKdjD(BigDecimal kdjD) {
        this.kdjD = kdjD;
    }

    /**
     * 读取 KDJ 的 J 值。
     */
    public BigDecimal getKdjJ() {
        return kdjJ;
    }

    /**
     * 设置 KDJ 的 J 值。
     */
    public void setKdjJ(BigDecimal kdjJ) {
        this.kdjJ = kdjJ;
    }

    /**
     * 读取 KDJ 的 RSV 值。
     */
    public BigDecimal getKdjRsv() {
        return kdjRsv;
    }

    /**
     * 设置 KDJ 的 RSV 值。
     */
    public void setKdjRsv(BigDecimal kdjRsv) {
        this.kdjRsv = kdjRsv;
    }

    /**
     * 读取 KDJ 窗口天数。
     */
    public Integer getKdjWindow() {
        return kdjWindow;
    }

    /**
     * 设置 KDJ 窗口天数。
     */
    public void setKdjWindow(Integer kdjWindow) {
        this.kdjWindow = kdjWindow;
    }

}
