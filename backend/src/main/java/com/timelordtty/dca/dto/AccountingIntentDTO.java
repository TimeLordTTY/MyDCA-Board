package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 文本记账解析结果，只表达候选记账意图，不代表正式流水或已入账结果。
 */
@Data
public class AccountingIntentDTO {
    /** 草稿来源类型，文本解析 MVP 默认使用 HERMES_TEXT。 */
    private String sourceType;
    /** 外部来源引用，例如 Hermes 消息 ID。 */
    private String sourceRef;
    /** 原始输入文本，供用户复核和后续重新解析。 */
    private String rawInput;
    /** 候选交易类型，首版仅识别 EXPENSE / INCOME，不确定时为空。 */
    private String txnType;
    /** 候选金额，不确定或缺失时为空。 */
    private BigDecimal amount;
    /** 候选备注，来自去除金额和账户提示后的主体文本。 */
    private String note;
    /** 候选账户 ID；首版规则解析不强行匹配账户，因此通常为空。 */
    private Long accountId;
    /** 账户名称提示，例如“余额宝生活费”，只供人工复核，不自动当成账户 ID。 */
    private String accountNameHint;
    /** 规则解析置信度，仅用于排序和提示，不作为自动入账依据。 */
    private BigDecimal confidence;
    /** 缺失字段列表，提示前端或 Hermes 后续补齐。 */
    private List<String> missingFields = new ArrayList<>();
    /** 标准化 intent JSON，保存到草稿表 parsed_payload_json。 */
    private String parsedPayloadJson;
}
