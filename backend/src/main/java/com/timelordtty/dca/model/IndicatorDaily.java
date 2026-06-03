package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日更指标表实体
 */
/**
 * 业务注释规范化: IndicatorDaily 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class IndicatorDaily {
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    /**
     * 业务注释规范化: 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     */
    private Long productId;
    /**
     * 业务注释规范化: tradeDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate tradeDate;
    /**
     * 业务注释规范化: windowDays 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Integer windowDays;
    /**
     * 业务注释规范化: pctRank 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal pctRank;
    /**
     * 业务注释规范化: qBuyPrice 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal qBuyPrice;
    /**
     * 业务注释规范化: qMidPrice 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal qMidPrice;
    /**
     * 业务注释规范化: qHighPrice 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal qHighPrice;
    /**
     * 业务注释规范化: peakClose 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal peakClose;
    /**
     * 业务注释规范化: drawdownFromPeak 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal drawdownFromPeak;
    /**
     * 业务注释规范化: ma20 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal ma20;
    /**
     * 业务注释规范化: ma60 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal ma60;

    // Getters and Setters
    /**
     * 业务注释规范化: 查询 getId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public Long getId() {
        return id;
    }

    /**
     * 业务注释规范化: 处理 setId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 业务注释规范化: 查询 getProductId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public Long getProductId() {
        return productId;
    }

    /**
     * 业务注释规范化: 处理 setProductId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setProductId(Long productId) {
        this.productId = productId;
    }

    /**
     * 业务注释规范化: 查询 getTradeDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public LocalDate getTradeDate() {
        return tradeDate;
    }

    /**
     * 业务注释规范化: 处理 setTradeDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param tradeDate tradeDate 日期字段，用于交易、确认、净值或统计周期口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    /**
     * 业务注释规范化: 查询 getWindowDays 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public Integer getWindowDays() {
        return windowDays;
    }

    /**
     * 业务注释规范化: 处理 setWindowDays 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param windowDays windowDays 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setWindowDays(Integer windowDays) {
        this.windowDays = windowDays;
    }

    /**
     * 业务注释规范化: 查询 getPctRank 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getPctRank() {
        return pctRank;
    }

    /**
     * 业务注释规范化: 处理 setPctRank 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param pctRank pctRank 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setPctRank(BigDecimal pctRank) {
        this.pctRank = pctRank;
    }

    /**
     * 业务注释规范化: 查询 getQBuyPrice 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getQBuyPrice() {
        return qBuyPrice;
    }

    /**
     * 业务注释规范化: 处理 setQBuyPrice 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param qBuyPrice qBuyPrice 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setQBuyPrice(BigDecimal qBuyPrice) {
        this.qBuyPrice = qBuyPrice;
    }

    /**
     * 业务注释规范化: 查询 getQMidPrice 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getQMidPrice() {
        return qMidPrice;
    }

    /**
     * 业务注释规范化: 处理 setQMidPrice 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param qMidPrice qMidPrice 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setQMidPrice(BigDecimal qMidPrice) {
        this.qMidPrice = qMidPrice;
    }

    /**
     * 业务注释规范化: 查询 getQHighPrice 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getQHighPrice() {
        return qHighPrice;
    }

    /**
     * 业务注释规范化: 处理 setQHighPrice 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param qHighPrice qHighPrice 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setQHighPrice(BigDecimal qHighPrice) {
        this.qHighPrice = qHighPrice;
    }

    /**
     * 业务注释规范化: 查询 getPeakClose 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getPeakClose() {
        return peakClose;
    }

    /**
     * 业务注释规范化: 处理 setPeakClose 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param peakClose peakClose 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setPeakClose(BigDecimal peakClose) {
        this.peakClose = peakClose;
    }

    /**
     * 业务注释规范化: 查询 getDrawdownFromPeak 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getDrawdownFromPeak() {
        return drawdownFromPeak;
    }

    /**
     * 业务注释规范化: 处理 setDrawdownFromPeak 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param drawdownFromPeak drawdownFromPeak 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setDrawdownFromPeak(BigDecimal drawdownFromPeak) {
        this.drawdownFromPeak = drawdownFromPeak;
    }

    /**
     * 业务注释规范化: 查询 getMa20 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getMa20() {
        return ma20;
    }

    /**
     * 业务注释规范化: 处理 setMa20 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param ma20 ma20 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setMa20(BigDecimal ma20) {
        this.ma20 = ma20;
    }

    /**
     * 业务注释规范化: 查询 getMa60 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getMa60() {
        return ma60;
    }

    /**
     * 业务注释规范化: 处理 setMa60 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param ma60 ma60 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setMa60(BigDecimal ma60) {
        this.ma60 = ma60;
    }
}
