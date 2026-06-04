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
     * 累计净值，用于基金长期收益和分红复权展示。
     */
    private BigDecimal accNav;
    /**
     * 单日收益率，表示基金当日净值变化比例。
     */
    private BigDecimal dailyReturn;
    /**
     * 基金分红金额，用于净值复权和收益统计。
     */
    private BigDecimal dividend;
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
     * 读取基金单日收益率，用于净值曲线和收益统计。
     */
    public BigDecimal getDailyReturn() {
        return dailyReturn;
    }

    /**
     * 设置基金单日收益率，保存净值变化比例。
     */
    public void setDailyReturn(BigDecimal dailyReturn) {
        this.dailyReturn = dailyReturn;
    }

    /**
     * 读取基金分红金额，用于净值复权和收益展示。
     */
    public BigDecimal getDividend() {
        return dividend;
    }

    /**
     * 设置基金分红金额，用于净值复权和收益统计。
     */
    public void setDividend(BigDecimal dividend) {
        this.dividend = dividend;
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
