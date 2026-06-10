package com.timelordtty.dca.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 草稿流水实体，对应 draft_ledger_entry 表。
 *
 * <p>草稿只保存识别结果、补全状态和确认关系，不参与账户余额、资产净值、持仓成本或流水统计计算。</p>
 */
@Data
public class DraftLedgerEntry {
    /** 草稿主键，用于定位一条待确认的自动记账候选记录。 */
    private Long id;
    /** 草稿归属用户 ID，限制个人视角只能处理自己的草稿。 */
    private Long ownerUserId;
    /** 草稿归属家庭 ID，用于家庭视角共享待确认草稿。 */
    private Long ownerFamilyId;
    /** 草稿来源类型，例如 manual、wechat、import、ocr，用于追踪候选记录来源。 */
    private String sourceType;
    /** 外部来源引用号，例如消息 ID、导入批次号或截图编号，只做追踪不直接入账。 */
    private String sourceRef;
    /** 原始输入文本或结构化来源摘要，用于人工复核识别结果。 */
    private String rawInput;
    /** 自动解析后的候选记账 JSON，不代表正式 ledger_txn 或 ledger_posting。 */
    private String parsedPayloadJson;
    /** 服务端预览 JSON，用于展示确认前将生成的记账摘要。 */
    private String previewPayloadJson;
    /** 草稿状态：DRAFT=待确认，CONFIRMED=已转正式流水，IGNORED=已忽略。 */
    private String status;
    /** 解析置信度，范围由上游解析器决定，仅用于排序和复核提示。 */
    private BigDecimal confidence;
    /** 缺失字段 JSON，用于告诉前端或人工哪些信息仍需补齐。 */
    private String missingFieldsJson;
    /** 确认后关联的正式流水 txn_id，首版快速收支确认会写入该字段。 */
    private String confirmTxnId;
    /** 确认后关联的订单 order_id，首版暂不自动生成订单但预留追踪字段。 */
    private String confirmOrderId;
    /** 忽略原因，记录人工为什么不把该草稿转为正式流水。 */
    private String ignoreReason;
    /** 草稿创建时间，用于列表排序和追踪候选记录生命周期。 */
    private LocalDateTime createdAt;
    /** 草稿最近更新时间，用于前端展示和并发排查。 */
    private LocalDateTime updatedAt;
    /** 草稿确认时间，只有状态进入 CONFIRMED 后才有值。 */
    private LocalDateTime confirmedAt;
    /** 草稿忽略时间，只有状态进入 IGNORED 后才有值。 */
    private LocalDateTime ignoredAt;
}
