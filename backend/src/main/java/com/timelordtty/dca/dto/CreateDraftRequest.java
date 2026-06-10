package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建草稿流水请求，只写入 draft_ledger_entry，不触发正式账本入账。
 */
@Data
public class CreateDraftRequest {
    /** 草稿来源类型，例如 manual、wechat、import、ocr，用于后续追踪候选记录来源。 */
    private String sourceType;
    /** 外部来源引用号，例如消息 ID、导入批次号或截图编号。 */
    private String sourceRef;
    /** 原始输入内容，用于人工复核和重新解析。 */
    private String rawInput;
    /** 自动解析得到的候选记账 JSON，不代表正式流水。 */
    private String parsedPayloadJson;
    /** 解析置信度，仅用于排序、筛选和复核提示。 */
    private BigDecimal confidence;
    /** 缺失字段 JSON，用于提示前端补齐必要记账信息。 */
    private String missingFieldsJson;
}
