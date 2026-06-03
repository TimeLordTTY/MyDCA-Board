package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 净值表实体
 */
public class Nav {
    private Long id;
    private Long productId;
    private LocalDate navDate;
    private BigDecimal nav;
    private BigDecimal accNav;
    private BigDecimal dailyReturn;
    private BigDecimal dividend;
    private String source;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public LocalDate getNavDate() {
        return navDate;
    }

    public void setNavDate(LocalDate navDate) {
        this.navDate = navDate;
    }

    public BigDecimal getNav() {
        return nav;
    }

    public void setNav(BigDecimal nav) {
        this.nav = nav;
    }

    public BigDecimal getAccNav() {
        return accNav;
    }

    public void setAccNav(BigDecimal accNav) {
        this.accNav = accNav;
    }

    public BigDecimal getDailyReturn() {
        return dailyReturn;
    }

    public void setDailyReturn(BigDecimal dailyReturn) {
        this.dailyReturn = dailyReturn;
    }

    public BigDecimal getDividend() {
        return dividend;
    }

    public void setDividend(BigDecimal dividend) {
        this.dividend = dividend;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
