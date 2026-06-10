package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 更新草稿流水请求，仅允许修改 DRAFT 状态草稿的候选内容。
 */
@Data
public class UpdateDraftRequest {
    /** 更新后的来源类型，用于修正草稿来源分类。 */
    private String sourceType;
    /** 更新后的来源引用号，用于修正外部消息或导入批次关联。 */
    private String sourceRef;
    /** 更新后的原始输入内容，用于人工修正识别来源。 */
    private String rawInput;
    /** 更新后的候选记账 JSON，不会直接写入正式账本。 */
    private String parsedPayloadJson;
    /** 更新后的解析置信度，用于前端复核排序。 */
    private BigDecimal confidence;
    /** 更新后的缺失字段 JSON，用于提示仍需补齐的信息。 */
    private String missingFieldsJson;
}
