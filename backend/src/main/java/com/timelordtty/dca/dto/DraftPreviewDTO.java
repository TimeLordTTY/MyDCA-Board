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
    /** 候选记账金额，必须为正数才能确认。 */
    private BigDecimal amount;
    /** 候选备注，会传递给正式流水的 note 字段。 */
    private String note;
    /** 当前草稿是否支持一键确认。 */
    private Boolean confirmSupported;
    /** 不能确认或需要补齐时的提示文案。 */
    private String message;
    /** 预览阶段识别出的缺失字段，例如 accountId、amount。 */
    private List<String> missingFields;
}
