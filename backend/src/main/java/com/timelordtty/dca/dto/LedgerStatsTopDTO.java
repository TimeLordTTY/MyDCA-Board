package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 流水统计 TopN 明细结果。
 *
 * <p>用于展示金额最高或最需要关注的流水，不改变原始流水记录。</p>
 */
@Data
public class LedgerStatsTopDTO {
    /** 交易流水号，用于跳转到流水详情。 */
    private String txnId;
    /** 流水类型，表示该明细属于收入、支出、投资或转账。 */
    private String txnType;
    /** 交易归属日的字符串展示值。 */
    private String tradeDate;
    /** 流水备注，帮助主人识别这笔明细的真实用途。 */
    private String note;
    /** 分类 ID，用于支出/收入分类展示。 */
    private Long categoryId;
    /** 关联账户 ID，用于账户维度追踪。 */
    private Long accountId;
    /** 关联账户名称，用于前端直接展示。 */
    private String accountName;
    /** TopN 排序使用的展示金额。 */
    private BigDecimal amount = BigDecimal.ZERO;
}
