package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 草稿确认预览 DTO，描述当前草稿如果确认会尝试生成的业务动作。
 */
@Data
public class DraftPreviewDTO {
    /** 草稿 ID，用于前端把预览结果与候选记录对应起来。 */
    private Long draftId;
    /** 候选流水类型，首版只支持 EXPENSE 和 INCOME 的快速记账确认。 */
    private String txnType;
    /** 候选现金账户 ID，确认 EXPENSE/INCOME 时会传给 QuickEntryService。 */
    private Long accountId;
    /** 候选账户名称，用于确认前复核本次草稿会影响哪个真实账户。 */
    private String accountName;
    /** 候选账户类型，例如 CASH、BANK、PAYMENT、MMF。 */
    private String accountType;
    /** 候选账户资金用途，例如 SPENDABLE、RESERVED、INVESTABLE。 */
    private String fundUsage;
    /** 候选记账金额，必须为正数才能确认。 */
    private BigDecimal amount;
    /** 对账户余额的影响方向：DECREASE、INCREASE 或 NONE。 */
    private String impactDirection;
    /** 对候选账户余额的预计变动金额，支出为负数，收入为正数。 */
    private BigDecimal accountDelta;
    /** 确认后是否会生成正式流水；预览阶段始终不会写正式账本。 */
    private Boolean willCreateLedgerTxn;
    /** 首版草稿确认不会生成订单。 */
    private Boolean willCreateOrder;
    /** 首版草稿确认不会生成待结算记录。 */
    private Boolean willCreateSettlement;
    /** 首版草稿确认不会影响持仓。 */
    private Boolean willAffectHolding;
    /** 候选备注，会传递给正式流水的 note 字段。 */
    private String note;
    /** 当前草稿是否支持一键确认。 */
    private Boolean confirmSupported;
    /** 不能确认或需要补齐时的提示文案。 */
    private String message;
    /** 预览阶段识别出的缺失字段，例如 accountId、amount。 */
    private List<String> missingFields;
    /** 预览阶段提示或风险说明，供用户确认前复核。 */
    private List<String> warnings;
}
