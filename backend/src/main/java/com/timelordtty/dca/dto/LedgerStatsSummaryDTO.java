package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
/**
 * 业务注释规范化: LedgerStatsSummaryDTO DTO 数据传输对象，用于承载请求参数或响应结果，属于前后端契约。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class LedgerStatsSummaryDTO {
    /**
     * 业务注释规范化: totalIncome 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal totalIncome = BigDecimal.ZERO;
    /**
     * 业务注释规范化: totalExpense 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal totalExpense = BigDecimal.ZERO;
    /**
     * 业务注释规范化: netCashflow 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal netCashflow = BigDecimal.ZERO;
    /**
     * 业务注释规范化: investmentInflow 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal investmentInflow = BigDecimal.ZERO;
    /**
     * 业务注释规范化: investmentOutflow 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal investmentOutflow = BigDecimal.ZERO;
    /**
     * 业务注释规范化: transferAmount 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal transferAmount = BigDecimal.ZERO;
    /**
     * 业务注释规范化: reimbursableExpense 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal reimbursableExpense = BigDecimal.ZERO;
    /**
     * 业务注释规范化: reimbursedAmount 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal reimbursedAmount = BigDecimal.ZERO;
    /**
     * 业务注释规范化: avgDailyExpense 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal avgDailyExpense = BigDecimal.ZERO;
    /**
     * 业务注释规范化: maxExpenseAmount 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal maxExpenseAmount = BigDecimal.ZERO;
    /**
     * 业务注释规范化: txnCount 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Integer txnCount = 0;
    /**
     * 业务注释规范化: expenseTxnCount 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Integer expenseTxnCount = 0;
    /**
     * 业务注释规范化: incomeTxnCount 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Integer incomeTxnCount = 0;
}
