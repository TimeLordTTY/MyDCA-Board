package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 流水统计分组结果。
 *
 * <p>用于按分类、账户、产品或流水类型聚合金额，所有字段均为只读统计结果。</p>
 */
@Data
public class LedgerStatsBreakdownDTO {
    /** 分组键，例如分类 ID、账户 ID、产品 ID 或流水类型代码。 */
    private String key;
    /** 分组展示名称，用于前端图表和列表展示。 */
    private String name;
    /** 本条结果对应的分组维度，便于前端识别 key 的业务含义。 */
    private String groupBy;
    /** 当前分组的主要展示金额，通常取收入、支出或投资流向中的业务主金额。 */
    private BigDecimal amount = BigDecimal.ZERO;
    /** 当前分组中的收入金额合计。 */
    private BigDecimal income = BigDecimal.ZERO;
    /** 当前分组中的支出金额合计。 */
    private BigDecimal expense = BigDecimal.ZERO;
    /** 当前分组中的投资流入金额合计。 */
    private BigDecimal investmentInflow = BigDecimal.ZERO;
    /** 当前分组中的投资流出金额合计。 */
    private BigDecimal investmentOutflow = BigDecimal.ZERO;
    /** 当前分组金额占总体金额的比例，服务层按同一统计口径计算。 */
    private BigDecimal percentage = BigDecimal.ZERO;
    /** 当前分组纳入统计的流水数量。 */
    private Integer txnCount = 0;
}
