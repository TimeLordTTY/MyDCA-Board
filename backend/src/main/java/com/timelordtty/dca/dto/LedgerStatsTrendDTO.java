package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
/**
 * 业务注释规范化: LedgerStatsTrendDTO DTO 数据传输对象，用于承载请求参数或响应结果，属于前后端契约。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class LedgerStatsTrendDTO {
    /**
     * 业务注释规范化: period 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String period;
    /**
     * 业务注释规范化: income 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal income = BigDecimal.ZERO;
    /**
     * 业务注释规范化: expense 业务字段，承载该对象在后端流程中的核心属性。
     */
    private BigDecimal expense = BigDecimal.ZERO;
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
     * 业务注释规范化: txnCount 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Integer txnCount = 0;
}
