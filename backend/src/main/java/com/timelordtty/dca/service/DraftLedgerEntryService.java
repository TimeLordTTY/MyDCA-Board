package com.timelordtty.dca.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.CreateDraftRequest;
import com.timelordtty.dca.dto.DraftLedgerEntryDTO;
import com.timelordtty.dca.dto.DraftPreviewDTO;
import com.timelordtty.dca.dto.UpdateDraftRequest;
import com.timelordtty.dca.mapper.DraftLedgerEntryMapper;
import com.timelordtty.dca.model.DraftLedgerEntry;
import com.timelordtty.dca.model.LedgerTxn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 草稿流水服务，负责 Phase3 自动记账候选记录的保存、预览、确认和忽略。
 *
 * <p>除 confirmDraft 明确调用 QuickEntryService 外，本服务不会写入正式账本、账户余额、持仓成本或订单数据。</p>
 */
@Service
public class DraftLedgerEntryService {
    /** 草稿流水持久化入口，只操作 draft_ledger_entry 表。 */
    private final DraftLedgerEntryMapper draftLedgerEntryMapper;
    /** 统一快速记账入口，确认 EXPENSE/INCOME 草稿时必须通过它写正式流水。 */
    private final QuickEntryService quickEntryService;
    /** JSON 编解码器，用于解析候选载荷并生成前端可展示的预览信息。 */
    private final ObjectMapper objectMapper;

    /**
     * 装配草稿 Mapper、快速记账服务和 JSON 编解码器。
     */
    public DraftLedgerEntryService(DraftLedgerEntryMapper draftLedgerEntryMapper,
                                   QuickEntryService quickEntryService,
                                   ObjectMapper objectMapper) {
        this.draftLedgerEntryMapper = draftLedgerEntryMapper;
        this.quickEntryService = quickEntryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建草稿流水候选记录，初始状态为 DRAFT，不会触发正式账本入账。
     */
    public DraftLedgerEntryDTO createDraft(Long userId, Long familyId, CreateDraftRequest request) {
        DraftLedgerEntry draft = new DraftLedgerEntry();
        draft.setOwnerUserId(userId);
        draft.setOwnerFamilyId(familyId);
        draft.setSourceType(defaultIfBlank(request.getSourceType(), "manual"));
        draft.setSourceRef(request.getSourceRef());
        draft.setRawInput(request.getRawInput());
        draft.setParsedPayloadJson(request.getParsedPayloadJson());
        draft.setConfidence(request.getConfidence());
        draft.setMissingFieldsJson(request.getMissingFieldsJson());
        draftLedgerEntryMapper.insert(draft);
        return DraftLedgerEntryDTO.fromModel(getVisibleDraft(userId, familyId, draft.getId()));
    }

    /**
     * 查询当前用户或家庭可见草稿列表，可按 DRAFT/CONFIRMED/IGNORED 状态过滤。
     */
    public List<DraftLedgerEntryDTO> listDrafts(Long userId, Long familyId, String status, Integer page, Integer pageSize) {
        int safePage = page != null && page > 0 ? page : 1;
        int safePageSize = pageSize != null && pageSize > 0 ? Math.min(pageSize, 100) : 20;
        int offset = (safePage - 1) * safePageSize;
        return draftLedgerEntryMapper.selectVisibleList(userId, familyId, normalizeStatusOrNull(status), offset, safePageSize)
                .stream()
                .map(DraftLedgerEntryDTO::fromModel)
                .toList();
    }

    /**
     * 查询当前用户或家庭可见草稿详情。
     */
    public DraftLedgerEntryDTO getDraft(Long userId, Long familyId, Long draftId) {
        return DraftLedgerEntryDTO.fromModel(getVisibleDraft(userId, familyId, draftId));
    }

    /**
     * 更新 DRAFT 状态草稿候选内容；已确认或已忽略草稿不允许再编辑。
     */
    @Transactional
    public DraftLedgerEntryDTO updateDraft(Long userId, Long familyId, Long draftId, UpdateDraftRequest request) {
        DraftLedgerEntry existing = getVisibleDraft(userId, familyId, draftId);
        requireDraftStatus(existing, "只有 DRAFT 状态草稿允许编辑");

        DraftLedgerEntry draft = new DraftLedgerEntry();
        draft.setId(draftId);
        draft.setSourceType(defaultIfBlank(request.getSourceType(), existing.getSourceType()));
        draft.setSourceRef(request.getSourceRef());
        draft.setRawInput(request.getRawInput());
        draft.setParsedPayloadJson(request.getParsedPayloadJson());
        draft.setConfidence(request.getConfidence());
        draft.setMissingFieldsJson(request.getMissingFieldsJson());
        int updated = draftLedgerEntryMapper.updateDraftContent(draft);
        if (updated != 1) {
            throw new RuntimeException("草稿更新失败，请刷新后重试");
        }
        return DraftLedgerEntryDTO.fromModel(getVisibleDraft(userId, familyId, draftId));
    }

    /**
     * 生成草稿确认预览，只更新 preview_payload_json，不写入正式 ledger_txn 或 ledger_posting。
     */
    @Transactional
    public DraftPreviewDTO previewDraft(Long userId, Long familyId, Long draftId) {
        DraftLedgerEntry draft = getVisibleDraft(userId, familyId, draftId);
        requireDraftStatus(draft, "只有 DRAFT 状态草稿允许生成预览");
        DraftPreviewDTO preview = buildPreview(draft);
        draftLedgerEntryMapper.updatePreview(draftId, toJson(preview));
        return preview;
    }

    /**
     * 确认草稿并转为正式流水；首版仅支持 EXPENSE/INCOME，且必须通过 QuickEntryService。
     */
    @Transactional
    public DraftLedgerEntryDTO confirmDraft(Long userId, Long familyId, Long draftId) {
        DraftLedgerEntry draft = draftLedgerEntryMapper.selectVisibleByIdForUpdate(draftId, userId, familyId);
        if (draft == null) {
            throw new RuntimeException("草稿不存在或无权限访问");
        }
        if ("CONFIRMED".equals(draft.getStatus())) {
            return DraftLedgerEntryDTO.fromModel(draft);
        }
        if ("IGNORED".equals(draft.getStatus())) {
            throw new RuntimeException("已忽略草稿不能确认");
        }

        DraftPreviewDTO preview = buildPreview(draft);
        draftLedgerEntryMapper.updatePreview(draftId, toJson(preview));
        if (!Boolean.TRUE.equals(preview.getConfirmSupported())) {
            throw new RuntimeException(preview.getMessage());
        }

        LedgerTxn txn;
        if ("EXPENSE".equals(preview.getTxnType())) {
            txn = quickEntryService.quickExpense(userId, preview.getAccountId(), preview.getAmount(), preview.getNote());
        } else if ("INCOME".equals(preview.getTxnType())) {
            txn = quickEntryService.quickIncome(userId, preview.getAccountId(), preview.getAmount(), preview.getNote());
        } else {
            throw new RuntimeException("首版草稿确认仅支持 EXPENSE/INCOME 快速记账");
        }

        int updated = draftLedgerEntryMapper.markConfirmed(draftId, txn.getTxnId(), null);
        if (updated != 1) {
            throw new RuntimeException("草稿确认状态更新失败，事务已回滚");
        }
        return DraftLedgerEntryDTO.fromModel(getVisibleDraft(userId, familyId, draftId));
    }

    /**
     * 忽略 DRAFT 状态草稿，记录原因后不再允许编辑或确认。
     */
    @Transactional
    public DraftLedgerEntryDTO ignoreDraft(Long userId, Long familyId, Long draftId, String ignoreReason) {
        DraftLedgerEntry draft = getVisibleDraft(userId, familyId, draftId);
        requireDraftStatus(draft, "只有 DRAFT 状态草稿允许忽略");
        int updated = draftLedgerEntryMapper.markIgnored(draftId, ignoreReason);
        if (updated != 1) {
            throw new RuntimeException("草稿忽略失败，请刷新后重试");
        }
        return DraftLedgerEntryDTO.fromModel(getVisibleDraft(userId, familyId, draftId));
    }

    /**
     * 查询可见草稿，不存在或越权时统一返回业务错误。
     */
    private DraftLedgerEntry getVisibleDraft(Long userId, Long familyId, Long draftId) {
        DraftLedgerEntry draft = draftLedgerEntryMapper.selectVisibleById(draftId, userId, familyId);
        if (draft == null) {
            throw new RuntimeException("草稿不存在或无权限访问");
        }
        return draft;
    }

    /**
     * 校验草稿仍处于 DRAFT 状态，防止已确认或已忽略草稿被重复修改。
     */
    private void requireDraftStatus(DraftLedgerEntry draft, String message) {
        if (!"DRAFT".equals(draft.getStatus())) {
            throw new RuntimeException(message);
        }
    }

    /**
     * 基于 parsed_payload_json 生成首版确认预览，支持 EXPENSE/INCOME 的必要字段校验。
     */
    private DraftPreviewDTO buildPreview(DraftLedgerEntry draft) {
        Map<String, Object> payload = parsePayload(draft.getParsedPayloadJson());
        DraftPreviewDTO preview = new DraftPreviewDTO();
        preview.setDraftId(draft.getId());
        preview.setTxnType(normalizeTxnType(firstString(payload, "txnType", "transactionType", "type")));
        preview.setAccountId(firstLong(payload, "accountId", "cashAccountId"));
        preview.setAmount(firstBigDecimal(payload, "amount"));
        preview.setNote(defaultIfBlank(firstString(payload, "note", "remark", "description"), draft.getRawInput()));

        List<String> missing = new ArrayList<>();
        if (preview.getTxnType() == null) {
            missing.add("txnType");
        }
        if (preview.getAccountId() == null) {
            missing.add("accountId");
        }
        if (preview.getAmount() == null || preview.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            missing.add("amount");
        }
        preview.setMissingFields(missing);

        boolean supportedType = "EXPENSE".equals(preview.getTxnType()) || "INCOME".equals(preview.getTxnType());
        boolean ready = supportedType && missing.isEmpty();
        preview.setConfirmSupported(ready);
        if (ready) {
            preview.setMessage("可确认：将通过 QuickEntryService 生成正式" + preview.getTxnType() + "流水");
        } else if (!supportedType) {
            preview.setMessage("首版草稿确认仅支持 EXPENSE/INCOME 快速记账");
        } else {
            preview.setMessage("草稿缺少必要字段：" + String.join(", ", missing));
        }
        return preview;
    }

    /**
     * 将候选载荷解析为 Map；空载荷按空对象处理，非法 JSON 视为不可确认草稿。
     */
    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("parsed_payload_json 不是有效 JSON，无法生成草稿预览");
        }
    }

    /**
     * 将预览对象写成 JSON 字符串，保存给前端展示和排查使用。
     */
    private String toJson(DraftPreviewDTO preview) {
        try {
            return objectMapper.writeValueAsString(preview);
        } catch (Exception e) {
            throw new RuntimeException("草稿预览 JSON 生成失败");
        }
    }

    /**
     * 从多个候选字段中取第一个非空字符串，用于兼容不同解析器输出。
     */
    private String firstString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * 从多个候选字段中取第一个可转为 Long 的账户 ID。
     */
    private Long firstLong(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null && !value.toString().isBlank()) {
                return Long.parseLong(value.toString());
            }
        }
        return null;
    }

    /**
     * 从候选字段中读取金额并转为 BigDecimal，避免 double 直接参与正式记账。
     */
    private BigDecimal firstBigDecimal(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value != null && !value.toString().isBlank()) {
            return new BigDecimal(value.toString());
        }
        return null;
    }

    /**
     * 统一草稿流水类型大小写，首版只允许 EXPENSE/INCOME 进入确认路径。
     */
    private String normalizeTxnType(String txnType) {
        return txnType == null ? null : txnType.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 统一列表筛选状态大小写，空值表示不过滤。
     */
    private String normalizeStatusOrNull(String status) {
        return status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 为空字符串提供默认值，避免草稿来源或备注出现无意义空白。
     */
    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
