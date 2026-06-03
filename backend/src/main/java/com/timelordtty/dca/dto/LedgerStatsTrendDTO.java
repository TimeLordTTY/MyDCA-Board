package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 流水统计趋势结果。
 *
 * <p>每个实例代表一个统计周期内的聚合结果，用于前端趋势图展示。</p>
 */
@Data
public class LedgerStatsTrendDTO {
    /** 周期标签，例如某日、某周或某月。 */
    private String period;
    /** 本周期收入金额合计。 */
    private BigDecimal income = BigDecimal.ZERO;
    /** 本周期支出金额合计。 */
    private BigDecimal expense = BigDecimal.ZERO;
    /** 本周期净现金流，等于 income - expense。 */
    private BigDecimal netCashflow = BigDecimal.ZERO;
    /** 本周期投资流入金额合计。 */
    private BigDecimal investmentInflow = BigDecimal.ZERO;
    /** 本周期投资流出金额合计。 */
    private BigDecimal investmentOutflow = BigDecimal.ZERO;
    /** 本周期纳入趋势统计的流水数量。 */
    private Integer txnCount = 0;
}
