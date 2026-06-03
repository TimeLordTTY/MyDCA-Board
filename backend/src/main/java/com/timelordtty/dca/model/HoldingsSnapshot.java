package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 持仓快照表实体（holdings_snapshot）
 */
/**
 * 业务注释规范化: HoldingsSnapshot 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class HoldingsSnapshot {
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    /**
     * 业务注释规范化: 所属用户 ID，用于限定个人数据权限和查询范围。
     */
    private Long userId;
    /**
     * 业务注释规范化: 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     */
    private Long productId;
    /**
     * 业务注释规范化: snapshotDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate snapshotDate;
    /**
     * 业务注释规范化: 产品份额，适用于基金、ETF、货币基金等按份额管理的资产。
     */
    private BigDecimal shares;
    /**
     * 业务注释规范化: cost 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal cost;
    private String costMethod; // AVERAGE/FIFO
    /**
     * 业务注释规范化: 产品净值，用于按份额折算市值、收益或确认金额。
     */
    private BigDecimal nav;
    /**
     * 业务注释规范化: navDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate navDate;
    /**
     * 业务注释规范化: marketValue 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal marketValue;
    /**
     * 业务注释规范化: unrealizedPnl 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal unrealizedPnl;
    /**
     * 业务注释规范化: returnRate 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal returnRate;
    /**
     * 业务注释规范化: fetchDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate fetchDate;
    /**
     * 业务注释规范化: isDirty 布尔标记，用于控制该记录在业务流程中的特殊状态。
     */
    private Boolean isDirty;
    /**
     * 业务注释规范化: dirtyFromDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate dirtyFromDate;
    /**
     * 业务注释规范化: 记录创建时间，用于审计和排序。
     */
    private LocalDateTime createdAt;
    /**
     * 业务注释规范化: 记录最后更新时间，用于审计和增量同步。
     */
    private LocalDateTime updatedAt;

    /**
     * 业务注释规范化: 查询 getId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public Long getId() { return id; }
    /**
     * 业务注释规范化: 处理 setId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setId(Long id) { this.id = id; }
    /**
     * 业务注释规范化: 查询 getUserId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public Long getUserId() { return userId; }
    /**
     * 业务注释规范化: 处理 setUserId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setUserId(Long userId) { this.userId = userId; }
    /**
     * 业务注释规范化: 查询 getProductId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public Long getProductId() { return productId; }
    /**
     * 业务注释规范化: 处理 setProductId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setProductId(Long productId) { this.productId = productId; }
    /**
     * 业务注释规范化: 查询 getSnapshotDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public LocalDate getSnapshotDate() { return snapshotDate; }
    /**
     * 业务注释规范化: 处理 setSnapshotDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param snapshotDate snapshotDate 日期字段，用于交易、确认、净值或统计周期口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    /**
     * 业务注释规范化: 查询 getShares 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getShares() { return shares; }
    /**
     * 业务注释规范化: 处理 setShares 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param shares 产品份额，适用于基金、ETF、货币基金等按份额管理的资产。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setShares(BigDecimal shares) { this.shares = shares; }
    /**
     * 业务注释规范化: 查询 getCost 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getCost() { return cost; }
    /**
     * 业务注释规范化: 处理 setCost 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param cost cost 金额字段，用于表达该场景下的资金规模或费用口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setCost(BigDecimal cost) { this.cost = cost; }
    /**
     * 业务注释规范化: 查询 getCostMethod 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public String getCostMethod() { return costMethod; }
    /**
     * 业务注释规范化: 处理 setCostMethod 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param costMethod costMethod 金额字段，用于表达该场景下的资金规模或费用口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setCostMethod(String costMethod) { this.costMethod = costMethod; }
    /**
     * 业务注释规范化: 查询 getNav 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getNav() { return nav; }
    /**
     * 业务注释规范化: 处理 setNav 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param nav 产品净值，用于按份额折算市值、收益或确认金额。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setNav(BigDecimal nav) { this.nav = nav; }
    /**
     * 业务注释规范化: 查询 getNavDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public LocalDate getNavDate() { return navDate; }
    /**
     * 业务注释规范化: 处理 setNavDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param navDate navDate 日期字段，用于交易、确认、净值或统计周期口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setNavDate(LocalDate navDate) { this.navDate = navDate; }
    /**
     * 业务注释规范化: 查询 getMarketValue 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getMarketValue() { return marketValue; }
    /**
     * 业务注释规范化: 处理 setMarketValue 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param marketValue marketValue 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }
    /**
     * 业务注释规范化: 查询 getUnrealizedPnl 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
    /**
     * 业务注释规范化: 处理 setUnrealizedPnl 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param unrealizedPnl unrealizedPnl 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setUnrealizedPnl(BigDecimal unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; }
    /**
     * 业务注释规范化: 查询 getReturnRate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getReturnRate() { return returnRate; }
    /**
     * 业务注释规范化: 处理 setReturnRate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param returnRate returnRate 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setReturnRate(BigDecimal returnRate) { this.returnRate = returnRate; }
    /**
     * 业务注释规范化: 查询 getFetchDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public LocalDate getFetchDate() { return fetchDate; }
    /**
     * 业务注释规范化: 处理 setFetchDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param fetchDate fetchDate 日期字段，用于交易、确认、净值或统计周期口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setFetchDate(LocalDate fetchDate) { this.fetchDate = fetchDate; }
    /**
     * 业务注释规范化: 查询 getIsDirty 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public Boolean getIsDirty() { return isDirty; }
    /**
     * 业务注释规范化: 处理 setIsDirty 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param isDirty isDirty 布尔标记，用于控制该记录在业务流程中的特殊状态。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setIsDirty(Boolean isDirty) { this.isDirty = isDirty; }
    /**
     * 业务注释规范化: 查询 getDirtyFromDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public LocalDate getDirtyFromDate() { return dirtyFromDate; }
    /**
     * 业务注释规范化: 处理 setDirtyFromDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param dirtyFromDate dirtyFromDate 日期字段，用于交易、确认、净值或统计周期口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setDirtyFromDate(LocalDate dirtyFromDate) { this.dirtyFromDate = dirtyFromDate; }
    /**
     * 业务注释规范化: 查询 getCreatedAt 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /**
     * 业务注释规范化: 处理 setCreatedAt 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param createdAt 记录创建时间，用于审计和排序。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    /**
     * 业务注释规范化: 查询 getUpdatedAt 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    /**
     * 业务注释规范化: 处理 setUpdatedAt 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param updatedAt 记录最后更新时间，用于审计和增量同步。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

