package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 人工结算预览（只读）。
 *
 * <p>v0.13.0 起，PENDING 订单必须先经过一次“只读结算预览”，主人在客户端看懂现金 / 持仓 / 手续费影响并
 * 二次确认后，才允许调用 confirm 真正生成 settlement_confirm 与内部账本。本 DTO 只描述“如果现在确认会发生什么”，
 * 生成它的过程绝不写 settlement_confirm、ledger_txn、ledger_posting，也不改 reserved_amount、initial_shares 或
 * order.status。</p>
 *
 * @author timelordtty
 * @since 0.13.0
 */
@Data
public class SettlementPreviewDTO {

    /** 订单业务单号 */
    private String orderId;

    /** 订单类型：BUY / SUBSCRIPTION / SELL / REDEMPTION */
    private String orderType;

    /** 订单类型中文名：买入 / 申购 / 卖出 / 赎回 */
    private String orderTypeLabel;

    /** 订单当前状态；只有 PENDING 才允许结算 */
    private String orderStatus;

    /** 产品 ID */
    private Long productId;

    /** 产品名称 */
    private String productName;

    /** 产品代码 */
    private String productCode;

    /** 币种 */
    private String currency;

    /** 本次结算使用的确认日期 */
    private LocalDate confirmDate;

    /** 本次结算使用的净值日期 */
    private LocalDate navDate;

    /** 实际确认净值 */
    private BigDecimal confirmNav;

    /** 实际确认份额（BUY / SUBSCRIPTION 使用） */
    private BigDecimal confirmShares;

    /** 实际确认金额（SELL / REDEMPTION 使用） */
    private BigDecimal confirmAmount;

    /** 实际手续费（null 输入表示按 BrokerFeeService 估算，明确输入 0 表示使用 0） */
    private BigDecimal confirmFee;

    /** 按现有规则计算的份额（BUY / SUBSCRIPTION 有值） */
    private BigDecimal computedShares;

    /** 按现有规则计算的金额（SELL / REDEMPTION 有值） */
    private BigDecimal computedAmount;

    /** 订单出资金额合计（BUY / SUBSCRIPTION 使用） */
    private BigDecimal totalFundingAmount;


    /** 中文警告：不阻断确认，但主人需要知道 */
    private List<String> warnings = new ArrayList<>();

    /** 是否允许确认结算；false 时禁止调用 confirm */
    private boolean confirmSupported;

    /** 中文阻断原因；confirmSupported=false 时必填 */
    private List<String> blockingReasons = new ArrayList<>();

    /** 结算影响预览分录（现金 / 持仓 / 手续费） */
    private List<SettlementPostingPreviewDTO> postingsPreview = new ArrayList<>();

    /** 结算影响中文摘要行，供客户端直接展示 */
    private List<String> summaryLines = new ArrayList<>();

    /** 结算后是否会写入 settlement_confirm（正常为 true） */
    private boolean willCreateSettlementConfirm;

    /** 结算后是否会写入 ledger_txn / ledger_posting（正常为 true） */
    private boolean willCreateLedgerTxn;

    /** 结算后持仓是否真实变化（按类型与真实账户计算结果返回） */
    private boolean willChangeHolding;

    /** 结算后现金是否真实变化（按类型与真实账户计算结果返回） */
    private boolean willChangeCash;

    /** 新鲜预览令牌；confirm 必须原样携带，任何关键输入 / 订单 / 资金 / 账户快照变化都会失效 */
    private String freshPreviewToken;

    /** 与 freshPreviewToken 等价的指纹，便于客户端以指纹语义展示与比对 */
    private String previewFingerprint;
}