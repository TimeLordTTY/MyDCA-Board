package com.timelordtty.dca.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.AccountingIntentDTO;
import com.timelordtty.dca.dto.CreateDraftRequest;
import com.timelordtty.dca.dto.DraftFromIntentRequest;
import com.timelordtty.dca.dto.DraftFromIntentResponse;
import com.timelordtty.dca.dto.DraftLedgerEntryDTO;
import com.timelordtty.dca.dto.ParseTextRequest;
import java.math.BigDecimal;
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
        assertEquals(new BigDecimal("32.5"), intent.getAmount());
        assertTrue(intent.getMissingFields().contains("txnType"));
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
}
