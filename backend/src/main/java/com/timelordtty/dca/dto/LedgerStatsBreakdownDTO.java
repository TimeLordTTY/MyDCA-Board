package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
/**
 * 业务注释规范化: LedgerStatsBreakdownDTO DTO 数据传输对象，用于承载请求参数或响应结果，属于前后端契约。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class LedgerStatsBreakdownDTO {
    /**
     * 业务注释规范化: key 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String key;
    /**
     * 业务注释规范化: name 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String name;
    /**
     * 业务注释规范化: groupBy 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String groupBy;
    /**
     * 业务注释规范化: 业务金额，通常以账户币种计价，正负含义由交易类型和分录方向决定。
     */
    private BigDecimal amount = BigDecimal.ZERO;
    /**
     * 业务注释规范化: income 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal income = BigDecimal.ZERO;
    /**
     * 业务注释规范化: expense 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal expense = BigDecimal.ZERO;
    /**
     * 业务注释规范化: investmentInflow 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal investmentInflow = BigDecimal.ZERO;
    /**
     * 业务注释规范化: investmentOutflow 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal investmentOutflow = BigDecimal.ZERO;
    /**
     * 业务注释规范化: percentage 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal percentage = BigDecimal.ZERO;
    /**
     * 业务注释规范化: txnCount 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Integer txnCount = 0;
}
