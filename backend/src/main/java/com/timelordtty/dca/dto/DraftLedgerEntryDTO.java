package com.timelordtty.dca.dto;

import com.timelordtty.dca.model.DraftLedgerEntry;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 草稿流水响应 DTO，向前端返回草稿状态、候选内容和确认追踪字段。
 */
@Data
public class DraftLedgerEntryDTO {
    /** 草稿主键，用于列表、详情、预览、确认和忽略接口定位记录。 */
    private Long id;
    /** 草稿归属用户 ID，用于前端展示归属和权限排查。 */
    private Long ownerUserId;
    /** 草稿归属家庭 ID，用于家庭视角共享候选记账。 */
    private Long ownerFamilyId;
    /** 草稿来源类型，例如 manual、wechat、import、ocr。 */
    private String sourceType;
    /** 外部来源引用号，只用于追踪来源，不代表正式账本编号。 */
    private String sourceRef;
    /** 原始输入内容，用于人工复核候选记账。 */
    private String rawInput;
    /** 自动解析得到的候选记账 JSON。 */
    private String parsedPayloadJson;
    /** 服务端生成的预览 JSON。 */
    private String previewPayloadJson;
    /** 草稿状态：DRAFT、CONFIRMED 或 IGNORED。 */
    private String status;
    /** 解析置信度，用于复核优先级展示。 */
    private BigDecimal confidence;
    /** 缺失字段 JSON，用于提示补齐信息。 */
    private String missingFieldsJson;
    /** 确认后关联的正式流水 txn_id。 */
    private String confirmTxnId;
    /** 确认后关联的订单 order_id，首版预留。 */
    private String confirmOrderId;
    /** 忽略原因，用于后续复盘为什么没有入账。 */
    private String ignoreReason;
    /** 草稿创建时间。 */
    private LocalDateTime createdAt;
    /** 草稿更新时间。 */
    private LocalDateTime updatedAt;
    /** 草稿确认时间。 */
    private LocalDateTime confirmedAt;
    /** 草稿忽略时间。 */
    private LocalDateTime ignoredAt;

    /**
     * 将持久化实体转换为 API 响应 DTO，避免控制器直接暴露模型转换细节。
     */
    public static DraftLedgerEntryDTO fromModel(DraftLedgerEntry draft) {
        if (draft == null) {
            return null;
        }
        DraftLedgerEntryDTO dto = new DraftLedgerEntryDTO();
        dto.setId(draft.getId());
        dto.setOwnerUserId(draft.getOwnerUserId());
        dto.setOwnerFamilyId(draft.getOwnerFamilyId());
        dto.setSourceType(draft.getSourceType());
        dto.setSourceRef(draft.getSourceRef());
        dto.setRawInput(draft.getRawInput());
        dto.setParsedPayloadJson(draft.getParsedPayloadJson());
        dto.setPreviewPayloadJson(draft.getPreviewPayloadJson());
        dto.setStatus(draft.getStatus());
        dto.setConfidence(draft.getConfidence());
        dto.setMissingFieldsJson(draft.getMissingFieldsJson());
        dto.setConfirmTxnId(draft.getConfirmTxnId());
        dto.setConfirmOrderId(draft.getConfirmOrderId());
        dto.setIgnoreReason(draft.getIgnoreReason());
        dto.setCreatedAt(draft.getCreatedAt());
        dto.setUpdatedAt(draft.getUpdatedAt());
        dto.setConfirmedAt(draft.getConfirmedAt());
        dto.setIgnoredAt(draft.getIgnoredAt());
        return dto;
    }
}
