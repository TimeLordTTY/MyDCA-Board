package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 流水统计查询的扁平化分录视图。
 *
 * <p>该对象由 Mapper 从 ledger_txn、ledger_posting、account 等表联查得到，服务层基于它做聚合，不能作为写入账本的命令对象。</p>
 */
@Data
public class LedgerStatsPostingDTO {
    /** 交易流水号，用于把同一笔复式记账下的多条分录重新聚合。 */
    private String txnId;
    /** 流水类型，决定该分录进入收入、支出、投资或转账统计口径。 */
    private String txnType;
    /** 流水所属用户 ID，用于个人视角的数据隔离。 */
    private Long userId;
    /** 流水所属家庭 ID，用于家庭视角聚合。 */
    private Long familyId;
    /** 关联投资产品 ID，非产品类生活流水可为空。 */
    private Long productId;
    /** 用户发起或系统记录该流水的时间。 */
    private LocalDateTime requestedAt;
    /** 交易归属日，统计区间按该日期过滤。 */
    private LocalDate tradeDate;
    /** 流水状态，已撤销或取消的记录由服务层决定是否纳入统计。 */
    private String status;
    /** 收入/支出分类 ID，用于分类统计。 */
    private Long categoryId;
    /** 支出是否具备报销属性。 */
    private Boolean isReimbursable;
    /** 支出是否已经被报销流水覆盖。 */
    private Boolean isReimbursed;
    /** 流水是否已经撤销。 */
    private Boolean isReversed;
    /** 流水备注，用于明细展示和人工核对。 */
    private String note;
    /** 分录主键 ID，对应 ledger_posting 的单条借贷记录。 */
    private Long postingId;
    /** 分录方向，DEBIT/CREDIT 决定账户余额增减方向。 */
    private String postingType;
    /** 分录关联账户 ID。 */
    private Long accountId;
    /** 分录关联账户名称，用于统计结果展示。 */
    private String accountName;
    /** 账户性质，例如 REAL、VIRTUAL，用于区分真实账户与统计账户。 */
    private String accountKind;
    /** 账户类型，例如 CASH、POSITION、EXPENSE、INCOME。 */
    private String accountType;
    /** 父账户 ID，用于按账户树汇总。 */
    private Long parentAccountId;
    /** 父账户名称，用于父账户筛选后的展示。 */
    private String parentAccountName;
    /** 分录金额，统计时按流水类型和分录方向转换为展示金额。 */
    private BigDecimal amount;
    /** 持仓份额，仅投资产品分录有意义。 */
    private BigDecimal shares;
    /** 币种代码，当前主要用于人民币金额展示，保留多币种扩展口径。 */
    private String currency;
}
