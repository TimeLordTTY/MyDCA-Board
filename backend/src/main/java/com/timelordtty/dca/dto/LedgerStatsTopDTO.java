package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
/**
 * 业务注释规范化: LedgerStatsTopDTO DTO 数据传输对象，用于承载请求参数或响应结果，属于前后端契约。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class LedgerStatsTopDTO {
    /**
     * 业务注释规范化: 流水业务 ID，用于聚合一笔复式记账交易下的所有分录。
     */
    private String txnId;
    /**
     * 业务注释规范化: 流水类型，决定该笔交易在收入、支出、投资、转账等统计口径中的归类。
     */
    private String txnType;
    /**
     * 业务注释规范化: tradeDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private String tradeDate;
    /**
     * 业务注释规范化: note 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String note;
    /**
     * 业务注释规范化: categoryId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     */
    private Long categoryId;
    /**
     * 业务注释规范化: 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     */
    private Long accountId;
    /**
     * 业务注释规范化: accountName 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String accountName;
    /**
     * 业务注释规范化: 业务金额，通常以账户币种计价，正负含义由交易类型和分录方向决定。
     */
    private BigDecimal amount = BigDecimal.ZERO;
}
