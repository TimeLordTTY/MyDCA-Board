package com.timelordtty.dca.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.AccountingIntentDTO;
import com.timelordtty.dca.dto.CreateDraftRequest;
import com.timelordtty.dca.dto.DraftFromIntentRequest;
import com.timelordtty.dca.dto.DraftFromIntentResponse;
import com.timelordtty.dca.dto.DraftLedgerEntryDTO;
import com.timelordtty.dca.dto.ParseTextRequest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 验证 Phase3 文本记账 MVP 只生成意图和草稿，不直接写正式账本。
 */
class AiAccountingServiceTest {

    private final DraftLedgerEntryService draftLedgerEntryService = mock(DraftLedgerEntryService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiAccountingService service = new AiAccountingService(draftLedgerEntryService, objectMapper);

    @Test
    void parseExpenseTextReturnsDraftIntentWithMissingAccountId() {
        ParseTextRequest request = new ParseTextRequest();
        request.setText("午饭花了32.5，用余额宝生活费");
        request.setSourceRef("hermes-msg-1");

        AccountingIntentDTO intent = service.parseText(request);

        assertEquals("HERMES_TEXT", intent.getSourceType());
        assertEquals("午饭花了32.5，用余额宝生活费", intent.getRawInput());
        assertEquals("EXPENSE", intent.getTxnType());
        assertEquals(new BigDecimal("32.5"), intent.getAmount());
        assertTrue(intent.getNote().contains("午饭"));
        assertNull(intent.getAccountId());
        assertEquals("余额宝生活费", intent.getAccountNameHint());
        assertTrue(intent.getMissingFields().contains("accountId"));
        assertFalse(intent.getMissingFields().contains("amount"));
        assertTrue(intent.getConfidence().compareTo(new BigDecimal("0.60")) >= 0);
        assertNotNull(intent.getParsedPayloadJson());
        verifyNoInteractions(draftLedgerEntryService);
    }

    @Test
    void parseIncomeTextDetectsIncomeType() {
        ParseTextRequest request = new ParseTextRequest();
        request.setText("收到工资8000到招商银行");

        AccountingIntentDTO intent = service.parseText(request);

        assertEquals("INCOME", intent.getTxnType());
        assertEquals(new BigDecimal("8000"), intent.getAmount());
        assertEquals("招商银行", intent.getAccountNameHint());
        assertTrue(intent.getMissingFields().contains("accountId"));
    }

    @Test
    void parseTextReportsMissingAmount() {
        ParseTextRequest request = new ParseTextRequest();
        request.setText("午饭花了，用现金");

        AccountingIntentDTO intent = service.parseText(request);

        assertEquals("EXPENSE", intent.getTxnType());
        assertNull(intent.getAmount());
        assertTrue(intent.getMissingFields().contains("amount"));
        assertTrue(intent.getMissingFields().contains("accountId"));
    }

    @Test
    void parseTextKeepsUnknownTypeAsMissing() {
        ParseTextRequest request = new ParseTextRequest();
        request.setText("余额宝生活费 32.5");

        AccountingIntentDTO intent = service.parseText(request);

        assertNull(intent.getTxnType());
        assertNull(intent.getAmount());
        assertTrue(intent.getMissingFields().contains("txnType"));
        assertTrue(intent.getMissingFields().contains("amount"));
    }

    @Test
    void parseTextRejectsBlankTextWithClearMessage() {
        ParseTextRequest request = new ParseTextRequest();
        request.setText("   ");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.parseText(request));

        assertTrue(error.getMessage().contains("text"));
        verifyNoInteractions(draftLedgerEntryService);
    }

    @Test
    void parseTextDoesNotExtractDateOrAccountDigitsAsAmount() {
        ParseTextRequest request = new ParseTextRequest();
        request.setText("2026-06-10 账户622202 午饭，用余额宝生活费");

        AccountingIntentDTO intent = service.parseText(request);

        assertNull(intent.getAmount());
        assertTrue(intent.getMissingFields().contains("amount"));
        assertTrue(intent.getMissingFields().contains("txnType"));
        verifyNoInteractions(draftLedgerEntryService);
    }

    @Test
    void parseTextDoesNotTreatNegativeAmountAsConfirmableAmount() {
        ParseTextRequest request = new ParseTextRequest();
        request.setText("午饭花了-32.5，用余额宝生活费");

        AccountingIntentDTO intent = service.parseText(request);

        assertNull(intent.getAmount());
        assertTrue(intent.getMissingFields().contains("amount"));
        verifyNoInteractions(draftLedgerEntryService);
    }

    @Test
    void parsedPayloadJsonContainsStandardFields() throws Exception {
        ParseTextRequest request = new ParseTextRequest();
        request.setText("午饭花了32.5，用余额宝生活费");

        AccountingIntentDTO intent = service.parseText(request);
        Map<?, ?> payload = objectMapper.readValue(intent.getParsedPayloadJson(), Map.class);

        assertTrue(payload.containsKey("txnType"));
        assertTrue(payload.containsKey("amount"));
        assertTrue(payload.containsKey("note"));
        assertTrue(payload.containsKey("accountId"));
        assertTrue(payload.containsKey("accountNameHint"));
        assertTrue(payload.containsKey("confidence"));
        assertTrue(payload.containsKey("missingFields"));
    }

    @Test
    void draftFromIntentCreatesDraftOnlyThroughDraftService() throws Exception {
        AccountingIntentDTO intent = new AccountingIntentDTO();
        intent.setSourceType("HERMES_TEXT");
        intent.setSourceRef("hermes-msg-2");
        intent.setRawInput("午饭花了32.5，用余额宝生活费");
        intent.setTxnType("EXPENSE");
        intent.setAmount(new BigDecimal("32.5"));
        intent.setNote("午饭");
        intent.setAccountNameHint("余额宝生活费");
        intent.setConfidence(new BigDecimal("0.70"));
        intent.setMissingFields(java.util.List.of("accountId"));
        intent.setParsedPayloadJson("{\"txnType\":\"EXPENSE\",\"amount\":32.5}");

        DraftLedgerEntryDTO draft = new DraftLedgerEntryDTO();
        draft.setId(99L);
        draft.setStatus("DRAFT");
        when(draftLedgerEntryService.createDraft(eq(10L), eq(20L), org.mockito.ArgumentMatchers.any(CreateDraftRequest.class)))
                .thenReturn(draft);

        DraftFromIntentRequest request = new DraftFromIntentRequest();
        request.setIntent(intent);
        DraftFromIntentResponse response = service.draftFromIntent(10L, 20L, request);

        assertEquals(99L, response.getDraft().getId());
        assertEquals("DRAFT", response.getDraft().getStatus());

        ArgumentCaptor<CreateDraftRequest> captor = ArgumentCaptor.forClass(CreateDraftRequest.class);
        verify(draftLedgerEntryService).createDraft(eq(10L), eq(20L), captor.capture());
        CreateDraftRequest createDraft = captor.getValue();
        assertEquals("HERMES_TEXT", createDraft.getSourceType());
        assertEquals("hermes-msg-2", createDraft.getSourceRef());
        assertEquals("午饭花了32.5，用余额宝生活费", createDraft.getRawInput());
        assertEquals(new BigDecimal("0.70"), createDraft.getConfidence());
        assertEquals(java.util.List.of("accountId"),
                objectMapper.readValue(createDraft.getMissingFieldsJson(), java.util.List.class));
        assertTrue(createDraft.getParsedPayloadJson().contains("\"txnType\":\"EXPENSE\""));
    }

    @Test
    void draftFromIntentRejectsEmptyIntent() {
        DraftFromIntentRequest request = new DraftFromIntentRequest();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.draftFromIntent(10L, 20L, request));

        assertTrue(error.getMessage().contains("intent"));
        verifyNoInteractions(draftLedgerEntryService);
    }

    @Test
    void draftFromIntentDefaultsMissingSourceTypeAndRegeneratesPayloadJson() {
        AccountingIntentDTO intent = new AccountingIntentDTO();
        intent.setRawInput("收到工资8000到招商银行");
        intent.setTxnType("INCOME");
        intent.setAmount(new BigDecimal("8000"));
        intent.setNote("工资");
        intent.setAccountNameHint("招商银行");

        DraftLedgerEntryDTO draft = new DraftLedgerEntryDTO();
        draft.setId(100L);
        draft.setStatus("DRAFT");
        when(draftLedgerEntryService.createDraft(eq(10L), eq(20L), org.mockito.ArgumentMatchers.any(CreateDraftRequest.class)))
                .thenReturn(draft);

        DraftFromIntentRequest request = new DraftFromIntentRequest();
        request.setIntent(intent);
        service.draftFromIntent(10L, 20L, request);

        ArgumentCaptor<CreateDraftRequest> captor = ArgumentCaptor.forClass(CreateDraftRequest.class);
        verify(draftLedgerEntryService).createDraft(eq(10L), eq(20L), captor.capture());
        CreateDraftRequest createDraft = captor.getValue();
        assertEquals("HERMES_TEXT", createDraft.getSourceType());
        assertTrue(createDraft.getParsedPayloadJson().contains("\"txnType\":\"INCOME\""));
        assertTrue(createDraft.getMissingFieldsJson().contains("accountId"));
    }

    @Test
    void draftFromIntentKeepsSupportedNonTextSourceType() {
        AccountingIntentDTO intent = new AccountingIntentDTO();
        intent.setSourceType("APP_FORM");
        intent.setRawInput("表单草稿");
        intent.setTxnType("EXPENSE");
        intent.setAmount(new BigDecimal("12.30"));

        DraftLedgerEntryDTO draft = new DraftLedgerEntryDTO();
        draft.setId(101L);
        draft.setStatus("DRAFT");
        when(draftLedgerEntryService.createDraft(eq(10L), eq(20L), org.mockito.ArgumentMatchers.any(CreateDraftRequest.class)))
                .thenReturn(draft);

        DraftFromIntentRequest request = new DraftFromIntentRequest();
        request.setIntent(intent);
        service.draftFromIntent(10L, 20L, request);

        ArgumentCaptor<CreateDraftRequest> captor = ArgumentCaptor.forClass(CreateDraftRequest.class);
        verify(draftLedgerEntryService).createDraft(eq(10L), eq(20L), captor.capture());
        assertEquals("APP_FORM", captor.getValue().getSourceType());
    }
}
