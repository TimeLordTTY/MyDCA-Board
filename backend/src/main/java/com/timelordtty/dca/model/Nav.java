package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 净值表实体
 */
/**
 * 业务注释规范化: Nav 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class Nav {
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    /**
     * 业务注释规范化: 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     */
    private Long productId;
    /**
     * 业务注释规范化: navDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate navDate;
    /**
     * 业务注释规范化: 产品净值，用于按份额折算市值、收益或确认金额。
     */
    private BigDecimal nav;
    /**
     * 业务注释规范化: accNav 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal accNav;
    /**
     * 业务注释规范化: dailyReturn 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal dailyReturn;
    /**
     * 业务注释规范化: dividend 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal dividend;
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
     * 业务注释规范化: 查询 getNavDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public LocalDate getNavDate() {
        return navDate;
    }

    /**
     * 业务注释规范化: 处理 setNavDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param navDate navDate 日期字段，用于交易、确认、净值或统计周期口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setNavDate(LocalDate navDate) {
        this.navDate = navDate;
    }

    /**
     * 业务注释规范化: 查询 getNav 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getNav() {
        return nav;
    }

    /**
     * 业务注释规范化: 处理 setNav 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param nav 产品净值，用于按份额折算市值、收益或确认金额。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setNav(BigDecimal nav) {
        this.nav = nav;
    }

    /**
     * 业务注释规范化: 查询 getAccNav 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getAccNav() {
        return accNav;
    }

    /**
     * 业务注释规范化: 处理 setAccNav 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accNav accNav 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setAccNav(BigDecimal accNav) {
        this.accNav = accNav;
    }

    /**
     * 业务注释规范化: 查询 getDailyReturn 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getDailyReturn() {
        return dailyReturn;
    }

    /**
     * 业务注释规范化: 处理 setDailyReturn 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param dailyReturn dailyReturn 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setDailyReturn(BigDecimal dailyReturn) {
        this.dailyReturn = dailyReturn;
    }

    /**
     * 业务注释规范化: 查询 getDividend 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getDividend() {
        return dividend;
    }

    /**
     * 业务注释规范化: 处理 setDividend 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param dividend dividend 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setDividend(BigDecimal dividend) {
        this.dividend = dividend;
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
