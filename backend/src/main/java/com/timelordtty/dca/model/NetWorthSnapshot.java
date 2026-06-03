package com.timelordtty.dca.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 净资产快照表实体（net_worth_snapshot）
 */
/**
 * 业务注释规范化: NetWorthSnapshot 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class NetWorthSnapshot {
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    /**
     * 业务注释规范化: 所属用户 ID，用于限定个人数据权限和查询范围。
     */
    private Long userId;
    /**
     * 业务注释规范化: 所属家庭 ID，用于家庭视角下的数据隔离。
     */
    private Long familyId;
    /**
     * 业务注释规范化: snapshotDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate snapshotDate;
    /**
     * 业务注释规范化: totalAssets 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal totalAssets;
    /**
     * 业务注释规范化: totalLiabilities 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal totalLiabilities;
    /**
     * 业务注释规范化: netWorth 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal netWorth;
    /**
     * 业务注释规范化: cashBalance 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal cashBalance;
    /**
     * 业务注释规范化: positionValue 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal positionValue;
    /**
     * 业务注释规范化: realizedPnl 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal realizedPnl;
    /**
     * 业务注释规范化: unrealizedPnl 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal unrealizedPnl;
    /**
     * 业务注释规范化: incomePnl 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal incomePnl;
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
     * 业务注释规范化: 查询 getFamilyId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public Long getFamilyId() { return familyId; }
    /**
     * 业务注释规范化: 处理 setFamilyId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param familyId 所属家庭 ID，用于家庭视角下的数据隔离。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
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
     * 业务注释规范化: 查询 getTotalAssets 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getTotalAssets() { return totalAssets; }
    /**
     * 业务注释规范化: 处理 setTotalAssets 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param totalAssets totalAssets 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setTotalAssets(BigDecimal totalAssets) { this.totalAssets = totalAssets; }
    /**
     * 业务注释规范化: 查询 getTotalLiabilities 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getTotalLiabilities() { return totalLiabilities; }
    /**
     * 业务注释规范化: 处理 setTotalLiabilities 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param totalLiabilities totalLiabilities 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setTotalLiabilities(BigDecimal totalLiabilities) { this.totalLiabilities = totalLiabilities; }
    /**
     * 业务注释规范化: 查询 getNetWorth 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getNetWorth() { return netWorth; }
    /**
     * 业务注释规范化: 处理 setNetWorth 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param netWorth netWorth 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setNetWorth(BigDecimal netWorth) { this.netWorth = netWorth; }
    /**
     * 业务注释规范化: 查询 getCashBalance 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getCashBalance() { return cashBalance; }
    /**
     * 业务注释规范化: 处理 setCashBalance 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param cashBalance cashBalance 金额字段，用于表达该场景下的资金规模或费用口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setCashBalance(BigDecimal cashBalance) { this.cashBalance = cashBalance; }
    /**
     * 业务注释规范化: 查询 getPositionValue 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getPositionValue() { return positionValue; }
    /**
     * 业务注释规范化: 处理 setPositionValue 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param positionValue positionValue 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setPositionValue(BigDecimal positionValue) { this.positionValue = positionValue; }
    /**
     * 业务注释规范化: 查询 getRealizedPnl 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    /**
     * 业务注释规范化: 处理 setRealizedPnl 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param realizedPnl realizedPnl 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
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
     * 业务注释规范化: 查询 getIncomePnl 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public BigDecimal getIncomePnl() { return incomePnl; }
    /**
     * 业务注释规范化: 处理 setIncomePnl 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param incomePnl incomePnl 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public void setIncomePnl(BigDecimal incomePnl) { this.incomePnl = incomePnl; }
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

