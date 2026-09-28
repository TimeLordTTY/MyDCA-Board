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
    /** 候选流水类型，支持 EXPENSE / INCOME / TRANSFER / BUY / SUBSCRIPTION / SELL / REDEMPTION。 */
    private String txnType;
    /** 候选账户 ID；TRANSFER 时表示转出账户，BUY / SUBSCRIPTION 时表示付款资金账户，SELL / REDEMPTION 时表示持仓来源账户。 */
    private Long accountId;
    /** 候选账户名称，用于确认前复核本次草稿会影响哪个真实账户。 */
    private String accountName;
    /** 候选账户类型，例如 CASH、BANK、PAYMENT、MMF。 */
    private String accountType;
    /** 候选账户资金用途，例如 SPENDABLE、RESERVED、INVESTABLE。 */
    private String fundUsage;
    /** TRANSFER 候选的转入账户 ID，SELL / REDEMPTION 候选的到账账户 ID；其他类型为空。 */
    private Long targetAccountId;
    /** TRANSFER 候选的转入账户名称，或 SELL / REDEMPTION 候选的到账账户名称，用于确认前复核资金去向。 */
    private String targetAccountName;
    /** TRANSFER 候选的转入账户类型，例如 CASH、BANK、PAYMENT、MMF。 */
    private String targetAccountType;
    /** TRANSFER 候选的转入账户资金用途，例如 SPENDABLE、RESERVED、INVESTABLE。 */
    private String targetFundUsage;
    /** 候选记账金额，必须为正数才能确认。 */
    private BigDecimal amount;
    /** 对候选（转出）账户余额的影响方向：DECREASE、INCREASE 或 NONE。 */
    private String impactDirection;
    /** 对候选账户余额的预计变动金额：支出为负数，收入为正数，TRANSFER 转出为负数，投资买入为负数。 */
    private BigDecimal accountDelta;
    /** TRANSFER 对转入账户余额的预计变动金额，为正数；其他类型的可确认预览为 0。 */
    private BigDecimal targetAccountDelta;
    /** BUY / SUBSCRIPTION / SELL / REDEMPTION 的订单类型，与 txnType 保持一致，便于前端按订单语义展示。 */
    private String orderType;
    /** BUY / SUBSCRIPTION / SELL / REDEMPTION 候选的真实产品 ID，必须由用户在 App / PC 明确选择。 */
    private Long productId;
    /** BUY / SUBSCRIPTION / SELL / REDEMPTION 候选的产品名称，来自产品主数据，仅用于展示。 */
    private String productName;
    /** BUY / SUBSCRIPTION / SELL / REDEMPTION 候选的产品代码，来自产品主数据，仅用于展示。 */
    private String productCode;
    /** BUY / SUBSCRIPTION / SELL / REDEMPTION 候选的产品资产类型，例如 ETF、FUND、BOND_REPO。 */
    private String productAssetType;
    /** BUY / SUBSCRIPTION / SELL / REDEMPTION 候选的产品币种，必须与资金账户 / 到账账户币种一致。 */
    private String productCurrency;
    /** BUY / SUBSCRIPTION 确认前资金账户的可用余额（balance - reserved_amount）。 */
    private BigDecimal availableBefore;
    /** BUY / SUBSCRIPTION 确认后待结算应收的预计变动金额，为正数；SELL / REDEMPTION 为 0。 */
    /** SELL / REDEMPTION 本次卖出 / 赎回份额，必须大于 0。 */
    private BigDecimal shares;
    /** SELL / REDEMPTION 持仓来源账户当前可用份额：真实持仓份额扣除同产品/来源下仍为 PENDING 的 SELL / REDEMPTION 占用份额。 */
    private BigDecimal availableShares;
    /** SELL / REDEMPTION 确认后预计剩余可用份额；不可确认时为 null。 */
    private BigDecimal remainingShares;
    /** SELL / REDEMPTION 份额影响中文说明，明确确认后不会立即减少持仓或增加到账余额。 */
    private String sharesMessage;
    private BigDecimal receivableDelta;
    /** BUY / SUBSCRIPTION / SELL / REDEMPTION 可选预期净值日期，仅作提示。 */
    private String expectedNavDate;
    /** BUY / SUBSCRIPTION / SELL / REDEMPTION 可选预期确认日期，仅作提示。 */
    private String expectedConfirmDate;
    /** BUY / SUBSCRIPTION 的付款说明文案，明确确认后会立即生成付款账本。 */
    private String fundingMessage;
    /** 确认后是否会生成正式流水；预览阶段始终不会写正式账本。 */
    private Boolean willCreateLedgerTxn;
    /** 确认后是否会生成 PENDING 订单；EXPENSE/INCOME/TRANSFER 为 false，SELL/REDEMPTION 为 true 但不生成账本。 */
    private Boolean willCreateOrder;
    /** 确认后是否会生成待结算记录；首版所有类型都为 false，SELL/REDEMPTION 只在后续人工结算时产生资金与持仓变化。 */
    private Boolean willCreateSettlement;
    /** 确认后是否会影响持仓；首版所有类型都为 false，持仓仍由后续结算决定。 */
    private Boolean willAffectHolding;
    /** 候选备注，会传递给正式流水的 note 字段。 */
    private String note;
    /** 当前草稿是否支持一键确认。 */
    private Boolean confirmSupported;
    /** 不能确认或需要补齐时的提示文案。 */
    private String message;
    /** 预览阶段识别出的缺失字段，例如 accountId、targetAccountId、productId、amount。 */
    private List<String> missingFields;
    /** 预览阶段提示或风险说明，供用户确认前复核。 */
    private List<String> warnings;
}
