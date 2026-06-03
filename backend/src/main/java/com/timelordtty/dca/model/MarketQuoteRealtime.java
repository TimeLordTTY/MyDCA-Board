package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实时行情表实体
 */
/**
 * 业务注释规范化: MarketQuoteRealtime 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class MarketQuoteRealtime {
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    /**
     * 业务注释规范化: 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     */
    private Long productId;
    /**
     * 业务注释规范化: quoteTime 时间字段，用于记录业务动作发生或审计时间。
     */
    private LocalDateTime quoteTime;
    /**
     * 业务注释规范化: 成交或行情价格，用于订单、成交、估值和持仓成本计算。
     */
    private BigDecimal price;
    /**
     * 业务注释规范化: prevClose 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal prevClose;
    /**
     * 业务注释规范化: pctChg 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal pctChg;
    /**
     * 业务注释规范化: volume 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal volume;
    /**
     * 业务注释规范化: 业务金额，通常以账户币种计价，正负含义由交易类型和分录方向决定。
     */
    private BigDecimal amount;
    /**
     * 业务注释规范化: iopv 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal iopv;
    /**
     * 业务注释规范化: premiumRate 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal premiumRate;
    /**
     * 业务注释规范化: openPrice 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal openPrice;
    /**
     * 业务注释规范化: highPrice 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal highPrice;
    /**
     * 业务注释规范化: lowPrice 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal lowPrice;
    /**
     * 业务注释规范化: source 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String source;

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
     * 业务注释规范化: 查询 getQuoteTime 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public LocalDateTime getQuoteTime() {
        return quoteTime;
    }

    /**
     * 业务注释规范化: 处理 setQuoteTime 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param quoteTime quoteTime 时间字段，用于记录业务动作发生或审计时间。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setQuoteTime(LocalDateTime quoteTime) {
        this.quoteTime = quoteTime;
    }

    /**
     * 业务注释规范化: 查询 getPrice 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * 业务注释规范化: 处理 setPrice 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param price 成交或行情价格，用于订单、成交、估值和持仓成本计算。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /**
     * 业务注释规范化: 查询 getPrevClose 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getPrevClose() {
        return prevClose;
    }

    /**
     * 业务注释规范化: 处理 setPrevClose 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param prevClose prevClose 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setPrevClose(BigDecimal prevClose) {
        this.prevClose = prevClose;
    }

    /**
     * 业务注释规范化: 查询 getPctChg 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getPctChg() {
        return pctChg;
    }

    /**
     * 业务注释规范化: 处理 setPctChg 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param pctChg pctChg 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setPctChg(BigDecimal pctChg) {
        this.pctChg = pctChg;
    }

    /**
     * 业务注释规范化: 查询 getVolume 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getVolume() {
        return volume;
    }

    /**
     * 业务注释规范化: 处理 setVolume 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param volume volume 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    /**
     * 业务注释规范化: 查询 getAmount 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * 业务注释规范化: 处理 setAmount 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param amount 业务金额，通常以账户币种计价，正负含义由交易类型和分录方向决定。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * 业务注释规范化: 查询 getIopv 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getIopv() {
        return iopv;
    }

    /**
     * 业务注释规范化: 处理 setIopv 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param iopv iopv 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setIopv(BigDecimal iopv) {
        this.iopv = iopv;
    }

    /**
     * 业务注释规范化: 查询 getPremiumRate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getPremiumRate() {
        return premiumRate;
    }

    /**
     * 业务注释规范化: 处理 setPremiumRate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param premiumRate premiumRate 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setPremiumRate(BigDecimal premiumRate) {
        this.premiumRate = premiumRate;
    }

    /**
     * 业务注释规范化: 查询 getOpenPrice 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    /**
     * 业务注释规范化: 处理 setOpenPrice 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param openPrice openPrice 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    /**
     * 业务注释规范化: 查询 getHighPrice 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getHighPrice() {
        return highPrice;
    }

    /**
     * 业务注释规范化: 处理 setHighPrice 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param highPrice highPrice 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    /**
     * 业务注释规范化: 查询 getLowPrice 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    /**
     * 业务注释规范化: 处理 setLowPrice 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param lowPrice lowPrice 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    /**
     * 业务注释规范化: 查询 getSource 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public String getSource() {
        return source;
    }

    /**
     * 业务注释规范化: 处理 setSource 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param source source 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setSource(String source) {
        this.source = source;
    }
}
