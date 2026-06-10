package com.timelordtty.dca.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.DraftPreviewDTO;
import com.timelordtty.dca.dto.UpdateDraftRequest;
import com.timelordtty.dca.mapper.DraftLedgerEntryMapper;
import com.timelordtty.dca.model.DraftLedgerEntry;
import com.timelordtty.dca.model.LedgerTxn;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 验证 Phase3 草稿流水服务的安全边界：草稿不进正式账本，确认必须走统一记账入口。
 */
class DraftLedgerEntryServiceTest {

    private final DraftLedgerEntryMapper mapper = mock(DraftLedgerEntryMapper.class);
    private final QuickEntryService quickEntryService = mock(QuickEntryService.class);
    private final DraftLedgerEntryService service = new DraftLedgerEntryService(mapper, quickEntryService, new ObjectMapper());

    @Test
    void previewDraftOnlyUpdatesPreviewPayloadAndDoesNotPostLedger() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{\"txnType\":\"EXPENSE\",\"accountId\":7,\"amount\":12.34,\"note\":\"午餐\"}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertEquals("EXPENSE", preview.getTxnType());
        assertEquals(new BigDecimal("12.34"), preview.getAmount());
        assertEquals(true, preview.getConfirmSupported());
        verify(mapper).updatePreview(eq(1L), any(String.class));
        verify(quickEntryService, never()).quickExpense(any(), any(), any(), any());
        verify(quickEntryService, never()).quickIncome(any(), any(), any(), any());
    }

    @Test
    void updateDraftRejectsConfirmedDraft() {
        DraftLedgerEntry draft = draft("CONFIRMED");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);

        UpdateDraftRequest request = new UpdateDraftRequest();
        request.setRawInput("改成新内容");

        assertThrows(RuntimeException.class, () -> service.updateDraft(10L, 20L, 1L, request));
        verify(mapper, never()).updateDraftContent(any());
    }

    @Test
    void confirmExpenseDraftUsesQuickEntryOnceAndMarksDraftConfirmed() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{\"txnType\":\"EXPENSE\",\"accountId\":7,\"amount\":12.34,\"note\":\"午餐\"}");
        LedgerTxn txn = new LedgerTxn();
        txn.setTxnId("TXN-TEST-1");

        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);
        when(quickEntryService.quickExpense(10L, 7L, new BigDecimal("12.34"), "午餐")).thenReturn(txn);
        when(mapper.markConfirmed(1L, "TXN-TEST-1", null)).thenReturn(1);

        service.confirmDraft(10L, 20L, 1L);

        verify(quickEntryService).quickExpense(10L, 7L, new BigDecimal("12.34"), "午餐");
        verify(mapper).markConfirmed(1L, "TXN-TEST-1", null);
    }

    @Test
    void confirmIncomeDraftUsesQuickEntryIncome() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{\"txnType\":\"INCOME\",\"accountId\":8,\"amount\":\"88.00\",\"note\":\"奖金\"}");
        LedgerTxn txn = new LedgerTxn();
        txn.setTxnId("TXN-INCOME-1");

        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);
        when(quickEntryService.quickIncome(10L, 8L, new BigDecimal("88.00"), "奖金")).thenReturn(txn);
        when(mapper.markConfirmed(1L, "TXN-INCOME-1", null)).thenReturn(1);

        service.confirmDraft(10L, 20L, 1L);

        verify(quickEntryService).quickIncome(10L, 8L, new BigDecimal("88.00"), "奖金");
        verify(quickEntryService, never()).quickExpense(any(), any(), any(), any());
        verify(mapper).markConfirmed(1L, "TXN-INCOME-1", null);
    }

    @Test
    void previewDraftReportsMissingFieldsWithoutPostingLedger() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{\"txnType\":\"EXPENSE\"}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertEquals(false, preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("accountId"));
        assertTrue(preview.getMissingFields().contains("amount"));
        verify(quickEntryService, never()).quickExpense(any(), any(), any(), any());
        verify(quickEntryService, never()).quickIncome(any(), any(), any(), any());
    }

    @Test
    void previewDraftRejectsInvalidJsonWithBusinessMessage() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{bad-json");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.previewDraft(10L, 20L, 1L));

        assertTrue(error.getMessage().contains("parsed_payload_json"));
        assertTrue(error.getMessage().contains("有效 JSON"));
        verify(mapper, never()).updatePreview(any(), any());
    }

    @Test
    void previewDraftRejectsNonNumericAccountIdWithBusinessMessage() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{\"txnType\":\"EXPENSE\",\"accountId\":\"abc\",\"amount\":12.34}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.previewDraft(10L, 20L, 1L));

        assertTrue(error.getMessage().contains("accountId"));
        assertTrue(error.getMessage().contains("数字"));
        verify(mapper, never()).updatePreview(any(), any());
    }

    @Test
    void previewDraftRejectsNonNumericAmountWithBusinessMessage() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{\"txnType\":\"EXPENSE\",\"accountId\":7,\"amount\":\"abc\"}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.previewDraft(10L, 20L, 1L));

        assertTrue(error.getMessage().contains("amount"));
        assertTrue(error.getMessage().contains("数字"));
        verify(mapper, never()).updatePreview(any(), any());
    }

    @Test
    void confirmUnsupportedTypeFailsWithoutPostingLedger() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{\"txnType\":\"BUY\",\"accountId\":7,\"amount\":12.34}");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.confirmDraft(10L, 20L, 1L));

        assertTrue(error.getMessage().contains("EXPENSE/INCOME"));
        verify(quickEntryService, never()).quickExpense(any(), any(), any(), any());
        verify(quickEntryService, never()).quickIncome(any(), any(), any(), any());
        verify(mapper, never()).markConfirmed(any(), any(), any());
    }

    @Test
    void confirmIgnoredDraftFailsWithoutPostingLedger() {
        DraftLedgerEntry draft = draft("IGNORED");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.confirmDraft(10L, 20L, 1L));

        assertTrue(error.getMessage().contains("忽略"));
        verify(quickEntryService, never()).quickExpense(any(), any(), any(), any());
        verify(quickEntryService, never()).quickIncome(any(), any(), any(), any());
    }

    @Test
    void confirmAlreadyConfirmedDraftIsIdempotentAndDoesNotPostAgain() {
        DraftLedgerEntry draft = draft("CONFIRMED");
        draft.setConfirmTxnId("TXN-DONE");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);

        service.confirmDraft(10L, 20L, 1L);

        verify(quickEntryService, never()).quickExpense(any(), any(), any(), any());
        verify(quickEntryService, never()).quickIncome(any(), any(), any(), any());
        verify(mapper, never()).markConfirmed(any(), any(), any());
    }

    private DraftLedgerEntry draft(String status) {
        DraftLedgerEntry draft = new DraftLedgerEntry();
        draft.setId(1L);
        draft.setOwnerUserId(10L);
        draft.setOwnerFamilyId(20L);
        draft.setStatus(status);
        return draft;
    }
}
