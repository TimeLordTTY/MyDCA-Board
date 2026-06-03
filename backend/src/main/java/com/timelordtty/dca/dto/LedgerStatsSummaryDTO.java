package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 流水统计总览结果。
 *
 * <p>金额字段均使用数据库中的人民币金额口径汇总，统计过程只读流水和分录，不修改账本、账户余额或持仓成本。</p>
 */
@Data
public class LedgerStatsSummaryDTO {
    /** 收入类流水合计金额，不包含内部转账。 */
    private BigDecimal totalIncome = BigDecimal.ZERO;
    /** 支出类流水合计金额，用于计算净现金流和日均支出。 */
    private BigDecimal totalExpense = BigDecimal.ZERO;
    /** 净现金流，等于 totalIncome - totalExpense。 */
    private BigDecimal netCashflow = BigDecimal.ZERO;
    /** 投资赎回、卖出、现金分红等流入金额。 */
    private BigDecimal investmentInflow = BigDecimal.ZERO;
    /** 投资买入、申购等流出金额。 */
    private BigDecimal investmentOutflow = BigDecimal.ZERO;
    /** 转账金额，仅在查询条件允许 includeTransfer 时参与展示。 */
    private BigDecimal transferAmount = BigDecimal.ZERO;
    /** 当前仍可报销但尚未报销的支出金额。 */
    private BigDecimal reimbursableExpense = BigDecimal.ZERO;
    /** 已经形成报销入账的金额。 */
    private BigDecimal reimbursedAmount = BigDecimal.ZERO;
    /** 按统计区间天数折算的日均支出。 */
    private BigDecimal avgDailyExpense = BigDecimal.ZERO;
    /** 单笔支出流水中的最大金额，用于支出峰值提示。 */
    private BigDecimal maxExpenseAmount = BigDecimal.ZERO;
    /** 纳入本次统计的流水数量。 */
    private Integer txnCount = 0;
    /** 纳入本次统计的支出流水数量。 */
    private Integer expenseTxnCount = 0;
    /** 纳入本次统计的收入流水数量。 */
    private Integer incomeTxnCount = 0;
}
