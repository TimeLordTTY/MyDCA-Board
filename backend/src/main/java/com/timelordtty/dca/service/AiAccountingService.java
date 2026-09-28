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
    /** 份额候选：形如 500份 / 1000.5份；只用于 SELL / REDEMPTION 候选提示。 */
    private static final Pattern SHARES_PATTERN = Pattern.compile("(?<![A-Za-z0-9.])([0-9]+(?:\\.[0-9]{1,4})?)\\s*份");
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
        String txnType = detectTxnType(normalizedText);
        intent.setTxnType(txnType);
        if (isInvestmentType(txnType)) {
            intent.setProductNameHint(extractProductNameHint(normalizedText));
            intent.setAmount(extractInvestmentAmount(normalizedText));
        } else if (isSellRedeemType(txnType)) {
            // 卖出 / 赎回只提取份额与产品名称提示；productId / 持仓来源 / 到账账户一律交给主人在 App / PC 明确选择。
            intent.setProductNameHint(extractSellRedeemProductNameHint(normalizedText));
            intent.setShares(extractShares(normalizedText));
        } else {
            intent.setAmount(txnType == null ? null : extractAmount(normalizedText));
        }
        if ("TRANSFER".equals(txnType)) {
            intent.setAccountNameHint(extractTransferSourceHint(normalizedText));
            intent.setTargetAccountNameHint(extractTransferTargetHint(normalizedText));
        } else if (!isSellRedeemType(txnType)) {
            intent.setAccountNameHint(extractAccountNameHint(normalizedText));
        }
        intent.setNote(extractNote(normalizedText, intent.getAmount(), intent.getShares(),
                intent.getAccountNameHint(), intent.getTargetAccountNameHint(), intent.getProductNameHint()));
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
        intent.setTargetAccountId(input.getTargetAccountId());
        intent.setTargetAccountNameHint(input.getTargetAccountNameHint());
        intent.setProductId(input.getProductId());
        intent.setProductNameHint(input.getProductNameHint());
        intent.setShares(input.getShares());
        intent.setSourceAccountId(input.getSourceAccountId());
        intent.setSourceAccountNameHint(input.getSourceAccountNameHint());
        intent.setExpectedNavDate(input.getExpectedNavDate());
        intent.setExpectedConfirmDate(input.getExpectedConfirmDate());
        intent.setConfidence(input.getConfidence() == null ? calculateConfidence(input) : input.getConfidence());
        intent.setMissingFields(input.getMissingFields() == null || input.getMissingFields().isEmpty()
                ? buildMissingFields(intent)
                : new ArrayList<>(input.getMissingFields()));
        intent.setParsedPayloadJson(defaultIfBlank(input.getParsedPayloadJson(), toIntentJson(intent)));
        return intent;
    }

    private String detectTxnType(String text) {
        if (detectTransfer(text)) {
            return "TRANSFER";
        }
        String investmentType = detectInvestment(text);
        if (investmentType != null) {
            return investmentType;
        }
        if (containsAny(text, "花了", "支出", "买", "消费", "付款", "支付")) {
            return "EXPENSE";
        }
        if (containsAny(text, "收入", "报销", "工资", "到账", "收到", "收款")) {
            return "INCOME";
        }
        return null;
    }

    /**
     * 识别明确转账语义；转账优先级高于“到账/付款”等可能造成 EXPENSE / INCOME 误判的关键词。
     */
    private boolean detectTransfer(String text) {
        if (containsAny(text, "转账", "转到", "转入", "转出")) {
            return true;
        }
        return text.contains("从") && text.contains("到");
    }

    /**
     * 识别明确投资语义：买入 / 申购 / 定投 / 卖出 / 赎回。
     *
     * <p>优先级高于 EXPENSE 关键词，避免“买入 XXX 1000”被“买”误判成支出；“买奶茶 30”不含“买入”，仍是 EXPENSE。
     * 卖出 / 赎回同样只识别候选类型与份额，不匹配真实产品、持仓来源或到账账户。</p>
     */
    private String detectInvestment(String text) {
        if (text.contains("买入")) {
            return "BUY";
        }
        if (text.contains("申购") || text.contains("定投")) {
            return "SUBSCRIPTION";
        }
        if (text.contains("卖出")) {
            return "SELL";
        }
        if (text.contains("赎回")) {
            return "REDEMPTION";
        }
        return null;
    }

    /** 判断候选类型是否为投资买入 / 申购，规则解析只负责识别，不负责匹配真实产品。 */
    private boolean isInvestmentType(String txnType) {
        return "BUY".equals(txnType) || "SUBSCRIPTION".equals(txnType);
    }

    /** 判断候选类型是否为卖出 / 赎回；规则解析只提取份额与产品名称提示。 */
    private boolean isSellRedeemType(String txnType) {
        return "SELL".equals(txnType) || "REDEMPTION".equals(txnType);
    }

    /**
     * 提取 SELL / REDEMPTION 产品名称提示：取“卖出 / 赎回”之后、数量或分隔符之前的内容。
     *
     * <p>只作为人工提示，禁止用它自动匹配真实 productId 或持仓来源。</p>
     */
    private String extractSellRedeemProductNameHint(String text) {
        for (String keyword : List.of("卖出", "赎回")) {
            int index = text.indexOf(keyword);
            if (index < 0) {
                continue;
            }
            String rest = text.substring(index + keyword.length()).trim();
            String hint = readUntilSeparator(rest).replaceFirst("^[0-9]+(\\.[0-9]+)?\\s*份?", "").trim();
            hint = hint.replaceFirst("[0-9]+(\\.[0-9]+)?\\s*份?$", "").trim();
            if (!hint.isBlank()) {
                return hint;
            }
        }
        return null;
    }

    /**
     * 提取 SELL / REDEMPTION 份额：取文本中最后一个形如 “500份” 的安全数量候选。
     *
     * <p>只作候选份额提示，最终可用份额必须由后端按真实持仓重新校验；这里不会自动占用份额。</p>
     */
    private BigDecimal extractShares(String text) {
        Matcher matcher = SHARES_PATTERN.matcher(text);
        BigDecimal last = null;
        while (matcher.find()) {
            if (isSafeAmountCandidate(text, matcher.start(1), matcher.end(1), matcher.group(1))) {
                last = new BigDecimal(matcher.group(1));
            }
        }
        return last;
    }

    /**
     * 提取 BUY / SUBSCRIPTION 产品名称提示：取“买入 / 申购 / 定投”之后、金额或分隔符之前的内容。
     *
     * <p>只作为人工提示，禁止用它自动匹配真实 productId。</p>
     */
    private String extractProductNameHint(String text) {
        for (String keyword : List.of("买入", "申购", "定投")) {
            int index = text.indexOf(keyword);
            if (index < 0) {
                continue;
            }
            String rest = text.substring(index + keyword.length()).trim();
            String withoutLeadingAmount = rest.replaceFirst("^[0-9]+(\\.[0-9]{1,2})?\\s*", "").trim();
            String hint = readUntilSeparator(withoutLeadingAmount);
            if (!hint.isBlank()) {
                return hint;
            }
        }
        return null;
    }

    /**
     * 提取 BUY / SUBSCRIPTION 金额：先剔除产品名称提示，再取剩余文本中最后一个安全金额候选。
     *
     * <p>这样“买入沪深300ETF 1000”取到 1000，而不会把产品名里的 300 误当成金额。</p>
     */
    private BigDecimal extractInvestmentAmount(String text) {
        String hint = extractProductNameHint(text);
        String scoped = text;
        if (hint != null && !hint.isBlank()) {
            int index = text.indexOf(hint);
            if (index >= 0) {
                scoped = text.substring(0, index) + " " + text.substring(index + hint.length());
            }
        }
        BigDecimal last = null;
        Matcher matcher = AMOUNT_PATTERN.matcher(scoped);
        while (matcher.find()) {
            if (isSafeAmountCandidate(scoped, matcher.start(1), matcher.end(1), matcher.group(1))) {
                last = new BigDecimal(matcher.group(1));
            }
        }
        return last;
    }

    private BigDecimal extractAmount(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        while (matcher.find()) {
            if (isSafeAmountCandidate(text, matcher.start(1), matcher.end(1), matcher.group(1))) {
                return new BigDecimal(matcher.group(1));
            }
        }
        return null;
    }

    private boolean isSafeAmountCandidate(String text, int start, int end, String candidate) {
        char previous = start > 0 ? text.charAt(start - 1) : '\0';
        char next = end < text.length() ? text.charAt(end) : '\0';
        if (previous == '-' || next == '-') {
            return false;
        }
        if (candidate.split("\\.")[0].length() >= 6) {
            return false;
        }
        return true;
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

    /**
     * 提取 TRANSFER 转出账户名称提示：取“从 X 转到/到 Y”中的 X，读到转账动词或分隔符即停止。
     */
    private String extractTransferSourceHint(String text) {
        int index = text.indexOf('从');
        if (index < 0) {
            return null;
        }
        String hint = readUntilTransferBoundary(text.substring(index + 1));
        return hint.isBlank() ? null : hint;
    }

    /**
     * 提取 TRANSFER 转入账户名称提示：优先取最后一个“到”之后的内容，其次取“转入”之后的内容。
     */
    private String extractTransferTargetHint(String text) {
        int toIndex = text.lastIndexOf('到');
        if (toIndex >= 0) {
            String hint = readUntilSeparator(text.substring(toIndex + 1));
            if (!hint.isBlank()) {
                return hint;
            }
        }
        int transferInIndex = text.indexOf("转入");
        if (transferInIndex >= 0) {
            String hint = readUntilSeparator(text.substring(transferInIndex + "转入".length()));
            if (!hint.isBlank()) {
                return hint;
            }
        }
        return null;
    }

    /**
     * 读取账户提示直到遇到转账动词或常规分隔符，避免把“从 A 转到 B”整体当成一个账户名。
     */
    private String readUntilTransferBoundary(String text) {
        int end = text.length();
        for (String separator : List.of("，", ",", "。", "；", ";", " ", "转", "到")) {
            int index = text.indexOf(separator);
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        return text.substring(0, end).trim();
    }

    private String extractNote(String text, BigDecimal amount, BigDecimal shares, String accountNameHint,
                               String targetAccountNameHint, String productNameHint) {
        String note = text;
        if (amount != null) {
            note = note.replaceFirst(Pattern.quote(amount.stripTrailingZeros().toPlainString()), "");
        }
        if (shares != null) {
            note = note.replaceFirst(Pattern.quote(shares.stripTrailingZeros().toPlainString()) + "份?", "");
        }
        if (productNameHint != null && !productNameHint.isBlank()) {
            note = note.replaceFirst(Pattern.quote(productNameHint), "");
        }
        if (accountNameHint != null) {
            note = note.replaceFirst("(用|从|转到|转入|转出|到)" + Pattern.quote(accountNameHint), "");
        }
        if (targetAccountNameHint != null) {
            note = note.replaceFirst("(转到|转入|到)" + Pattern.quote(targetAccountNameHint), "");
        }
        note = note.replaceAll("[，,。；;]", " ")
                .replace("买入", "")
                .replace("申购", "")
                .replace("定投", "")
                .replace("卖出", "")
                .replace("赎回", "")
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
        boolean sellRedeem = isSellRedeemType(intent.getTxnType());
        if (intent.getTxnType() == null || intent.getTxnType().isBlank()) {
            missingFields.add("txnType");
        }
        if (sellRedeem) {
            if (intent.getShares() == null || intent.getShares().compareTo(BigDecimal.ZERO) <= 0) {
                missingFields.add("shares");
            }
            if (intent.getSourceAccountId() == null && intent.getAccountId() == null) {
                missingFields.add("sourceAccountId");
            }
            if (intent.getTargetAccountId() == null) {
                missingFields.add("targetAccountId");
            }
        } else if (intent.getAmount() == null || intent.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            missingFields.add("amount");
        }
        if (!sellRedeem && intent.getAccountId() == null) {
            missingFields.add("accountId");
        }
        if ("TRANSFER".equals(intent.getTxnType()) && intent.getTargetAccountId() == null) {
            missingFields.add("targetAccountId");
        }
        if ((isInvestmentType(intent.getTxnType()) || sellRedeem) && intent.getProductId() == null) {
            missingFields.add("productId");
        }
        return missingFields;
    }

    private BigDecimal calculateConfidence(AccountingIntentDTO intent) {
        boolean hasType = intent.getTxnType() != null && !intent.getTxnType().isBlank();
        BigDecimal measure = isSellRedeemType(intent.getTxnType()) ? intent.getShares() : intent.getAmount();
        boolean hasAmount = measure != null && measure.compareTo(BigDecimal.ZERO) > 0;
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
        payload.put("targetAccountId", intent.getTargetAccountId());
        payload.put("targetAccountNameHint", intent.getTargetAccountNameHint());
        payload.put("productId", intent.getProductId());
        payload.put("productNameHint", intent.getProductNameHint());
        payload.put("shares", intent.getShares());
        payload.put("sourceAccountId", intent.getSourceAccountId());
        payload.put("sourceAccountNameHint", intent.getSourceAccountNameHint());
        payload.put("expectedNavDate", intent.getExpectedNavDate());
        payload.put("expectedConfirmDate", intent.getExpectedConfirmDate());
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
