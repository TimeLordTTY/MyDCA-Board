package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 净值表实体
 */
public class Nav {
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
    private LocalDate navDate;
    /**
     * 单位净值，用于持仓市值和盈亏计算。
     */
    private BigDecimal nav;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal accNav;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal dailyReturn;
    /**
     * 请求或响应字段，用于前后端传递该场景的业务信息。
     */
    private BigDecimal dividend;
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
     * 返回业务日期，用于交易归属、确认或统计周期判定。
     */
    public LocalDate getNavDate() {
        return navDate;
    }

    /**
     * 设置业务日期，用于交易归属、确认或统计周期判定。
     */
    public void setNavDate(LocalDate navDate) {
        this.navDate = navDate;
    }

    /**
     * 返回单位净值，用于持仓市值和盈亏计算。
     */
    public BigDecimal getNav() {
        return nav;
    }

    /**
     * 设置单位净值，用于持仓市值和盈亏计算。
     */
    public void setNav(BigDecimal nav) {
        this.nav = nav;
    }

    /**
     * 返回行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public BigDecimal getAccNav() {
        return accNav;
    }

    /**
     * 设置行情或净值字段，用于估值、持仓市值或历史走势展示。
     */
    public void setAccNav(BigDecimal accNav) {
        this.accNav = accNav;
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public BigDecimal getDailyReturn() {
        return dailyReturn;
    }

    /**
     * 设置当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public void setDailyReturn(BigDecimal dailyReturn) {
        this.dailyReturn = dailyReturn;
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public BigDecimal getDividend() {
        return dividend;
    }

    /**
     * 设置当前场景的业务数据，用于前后端传递或服务层计算。
     */
    public void setDividend(BigDecimal dividend) {
        this.dividend = dividend;
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
