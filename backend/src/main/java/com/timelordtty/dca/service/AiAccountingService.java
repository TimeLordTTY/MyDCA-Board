package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.AccountingIntentDTO;
import com.timelordtty.dca.dto.CreateDraftRequest;
import com.timelordtty.dca.dto.DraftFromIntentRequest;
import com.timelordtty.dca.dto.DraftFromIntentResponse;
import com.timelordtty.dca.dto.DraftLedgerEntryDTO;
import com.timelordtty.dca.dto.ParseTextRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase3 文本记账解析服务，首版只做规则解析和草稿生成，不调用真实大模型，也不写正式账本。
 */
@Service
public class AiAccountingService {
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?<![A-Za-z0-9.])([0-9]+(?:\\.[0-9]{1,2})?)");
    private static final BigDecimal HIGH_CONFIDENCE = new BigDecimal("0.70");
    private static final BigDecimal MEDIUM_CONFIDENCE = new BigDecimal("0.55");

    /** 草稿服务是唯一允许本 MVP 写入 draft_ledger_entry 的入口。 */
    private final DraftLedgerEntryService draftLedgerEntryService;
    /** JSON 编码器用于生成 parsedPayloadJson 和 missingFieldsJson。 */
    private final ObjectMapper objectMapper;

    public AiAccountingService(DraftLedgerEntryService draftLedgerEntryService, ObjectMapper objectMapper) {
        this.draftLedgerEntryService = draftLedgerEntryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 将自然语言文本解析成候选记账意图；缺失或不确定信息会进入 missingFields，等待人工确认。
     */
    public AccountingIntentDTO parseText(ParseTextRequest request) {
        String rawText = request == null ? null : request.getText();
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }

        String normalizedText = rawText.trim();
        AccountingIntentDTO intent = new AccountingIntentDTO();
        intent.setSourceType("HERMES_TEXT");
        intent.setSourceRef(request.getSourceRef());
        intent.setRawInput(normalizedText);
        intent.setTxnType(detectTxnType(normalizedText));
        intent.setAmount(extractAmount(normalizedText));
        intent.setAccountNameHint(extractAccountNameHint(normalizedText));
        intent.setNote(extractNote(normalizedText, intent.getAmount(), intent.getAccountNameHint()));
        intent.setConfidence(calculateConfidence(intent));
        intent.setMissingFields(buildMissingFields(intent));
        intent.setParsedPayloadJson(toIntentJson(intent));
        return intent;
    }

    /**
     * 把 intent 写入草稿表；该方法只创建 DRAFT，不调用 QuickEntryService，也不确认正式入账。
     */
    public DraftFromIntentResponse draftFromIntent(Long userId, Long familyId, DraftFromIntentRequest request) {
        if (request == null || request.getIntent() == null) {
            throw new IllegalArgumentException("intent 不能为空");
        }
        AccountingIntentDTO intent = normalizeIntent(request.getIntent());

        CreateDraftRequest createDraftRequest = new CreateDraftRequest();
        createDraftRequest.setSourceType(defaultIfBlank(intent.getSourceType(), "HERMES_TEXT"));
        createDraftRequest.setSourceRef(intent.getSourceRef());
        createDraftRequest.setRawInput(intent.getRawInput());
        createDraftRequest.setParsedPayloadJson(intent.getParsedPayloadJson());
        createDraftRequest.setConfidence(intent.getConfidence());
        createDraftRequest.setMissingFieldsJson(toJson(intent.getMissingFields()));

        DraftLedgerEntryDTO draft = draftLedgerEntryService.createDraft(userId, familyId, createDraftRequest);
        DraftFromIntentResponse response = new DraftFromIntentResponse();
        response.setIntent(intent);
        response.setDraft(draft);
        return response;
    }

    private AccountingIntentDTO normalizeIntent(AccountingIntentDTO input) {
        AccountingIntentDTO intent = new AccountingIntentDTO();
        intent.setSourceType(defaultIfBlank(input.getSourceType(), "HERMES_TEXT"));
        intent.setSourceRef(input.getSourceRef());
        intent.setRawInput(input.getRawInput());
        intent.setTxnType(defaultIfBlank(input.getTxnType(), null));
        intent.setAmount(input.getAmount());
        intent.setNote(input.getNote());
        intent.setAccountId(input.getAccountId());
        intent.setAccountNameHint(input.getAccountNameHint());
        intent.setConfidence(input.getConfidence() == null ? calculateConfidence(input) : input.getConfidence());
        intent.setMissingFields(input.getMissingFields() == null ? buildMissingFields(intent) : new ArrayList<>(input.getMissingFields()));
        intent.setParsedPayloadJson(defaultIfBlank(input.getParsedPayloadJson(), toIntentJson(intent)));
        return intent;
    }

    private String detectTxnType(String text) {
        if (containsAny(text, "花了", "支出", "买", "消费", "付款", "支付")) {
            return "EXPENSE";
        }
        if (containsAny(text, "收入", "报销", "工资", "到账", "收到", "收款")) {
            return "INCOME";
        }
        return null;
    }

    private BigDecimal extractAmount(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return new BigDecimal(matcher.group(1));
    }

    private String extractAccountNameHint(String text) {
        for (int i = 0; i < text.length(); i++) {
            char marker = text.charAt(i);
            if (marker != '用' && marker != '到' && marker != '从') {
                continue;
            }
            if (marker == '到' && i > 0 && text.charAt(i - 1) == '收') {
                continue;
            }
            String hint = readUntilSeparator(text.substring(i + 1));
            if (!hint.isBlank()) {
                return hint;
            }
        }
        return null;
    }

    private String readUntilSeparator(String text) {
        int end = text.length();
        for (String separator : List.of("，", ",", "。", "；", ";", " ")) {
            int index = text.indexOf(separator);
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        return text.substring(0, end).trim();
    }

    private String extractNote(String text, BigDecimal amount, String accountNameHint) {
        String note = text;
        if (amount != null) {
            note = note.replaceFirst(Pattern.quote(amount.stripTrailingZeros().toPlainString()), "");
        }
        if (accountNameHint != null) {
            note = note.replaceFirst("(用|到|从)" + Pattern.quote(accountNameHint), "");
        }
        note = note.replaceAll("[，,。；;]", " ")
                .replace("花了", "")
                .replace("支出", "")
                .replace("消费", "")
                .replace("收到", "")
                .replace("到账", "")
                .replaceAll("\\s+", " ")
                .trim();
        return note.isBlank() ? text : note;
    }

    private List<String> buildMissingFields(AccountingIntentDTO intent) {
        List<String> missingFields = new ArrayList<>();
        if (intent.getTxnType() == null || intent.getTxnType().isBlank()) {
            missingFields.add("txnType");
        }
        if (intent.getAmount() == null || intent.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            missingFields.add("amount");
        }
        if (intent.getAccountId() == null) {
            missingFields.add("accountId");
        }
        return missingFields;
    }

    private BigDecimal calculateConfidence(AccountingIntentDTO intent) {
        boolean hasType = intent.getTxnType() != null && !intent.getTxnType().isBlank();
        boolean hasAmount = intent.getAmount() != null && intent.getAmount().compareTo(BigDecimal.ZERO) > 0;
        return hasType && hasAmount ? HIGH_CONFIDENCE : MEDIUM_CONFIDENCE;
    }

    private String toIntentJson(AccountingIntentDTO intent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", intent.getSourceType());
        payload.put("sourceRef", intent.getSourceRef());
        payload.put("rawInput", intent.getRawInput());
        payload.put("txnType", intent.getTxnType());
        payload.put("amount", intent.getAmount());
        payload.put("note", intent.getNote());
        payload.put("accountId", intent.getAccountId());
        payload.put("accountNameHint", intent.getAccountNameHint());
        payload.put("confidence", intent.getConfidence());
        payload.put("missingFields", intent.getMissingFields());
        return toJson(payload);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("文本记账解析结果 JSON 生成失败");
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
