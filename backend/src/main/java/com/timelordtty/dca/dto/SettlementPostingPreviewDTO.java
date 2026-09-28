package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 结算影响预览中的单条分录（只读，不代表已经落账）。
 *
 * <p>该对象只用于“主人确认前看懂结算影响”：每条分录说明会对哪个账户产生多少现金 / 份额变动，
 * 以及对应的中文说明。preview 阶段绝对不会写入 ledger_txn / ledger_posting。</p>
 *
 * @author timelordtty
 * @since 0.13.0
 */
@Data
public class SettlementPostingPreviewDTO {

    /** 受影响账户 ID；账户将在结算时自动创建时可能为 null */
    private Long accountId;

    /** 受影响账户名称；账户尚未创建时为结算时会创建的名称 */
    private String accountName;

    /** 分录语义类型：CASH / POSITION / RECEIVABLE / FEE / INCOME */
    private String accountType;

    /** 分录方向：DEBIT（增加该账户余额/持仓）/ CREDIT（减少该账户余额/持仓） */
    private String postingType;

    /** 分录金额（不含份额时为唯一变动量） */
    private BigDecimal amount;

    /** 分录份额（仅 POSITION 分录有意义） */
    private BigDecimal shares;

    /** 币种 */
    private String currency;

    /** 中文说明：例如“现金账户：+1,000.00 元” */
    private String description;
}