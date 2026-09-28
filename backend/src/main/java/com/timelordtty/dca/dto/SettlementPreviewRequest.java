package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 人工结算预览 / 确认请求。
 *
 * <p>preview 与 confirm 共用同一份输入，避免“预览看到的输入”和“确认时真正使用的输入”不一致。
 * 日期使用字符串接收，非法值不会直接抛异常，而是作为阻断原因返回，方便客户端提示主人修正。</p>
 *
 * @author timelordtty
 * @since 0.13.0
 */
@Data
public class SettlementPreviewRequest {

    /** 订单业务单号 */
    private String orderId;

    /** 确认日期（ISO，例如 2026-09-28） */
    private String confirmDate;

    /** 净值日期（ISO，例如 2026-09-28） */
    private String navDate;

    /** 实际确认净值，必须大于 0 */
    private BigDecimal confirmNav;

    /** 实际确认份额（BUY / SUBSCRIPTION 使用；为空时按金额与净值计算） */
    private BigDecimal confirmShares;

    /** 实际确认金额（SELL / REDEMPTION 使用，必须大于 0） */
    private BigDecimal confirmAmount;

    /** 实际手续费；null 表示按 BrokerFeeService 估算，明确输入 0 表示使用 0 */
    private BigDecimal confirmFee;

    /** fresh preview 令牌；只有 confirm 需要携带，preview 忽略该字段 */
    private String freshPreviewToken;

    /** 备注 */
    private String note;
}