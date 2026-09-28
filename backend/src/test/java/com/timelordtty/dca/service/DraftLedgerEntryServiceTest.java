package com.timelordtty.dca.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.CreateDraftRequest;
import com.timelordtty.dca.dto.DraftLedgerEntryDTO;
import com.timelordtty.dca.dto.DraftPreviewDTO;
import com.timelordtty.dca.dto.UpdateDraftRequest;
import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.DraftLedgerEntryMapper;
import com.timelordtty.dca.mapper.ProductMasterMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.DraftLedgerEntry;
import com.timelordtty.dca.model.LedgerTxn;
import com.timelordtty.dca.model.ProductMaster;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

/**
 * 验证 Phase3 草稿流水服务的安全边界：草稿不进正式账本，确认必须走统一记账入口。
 */
class DraftLedgerEntryServiceTest {

    @BeforeEach
    void stubPreviewUpdate() {
        org.mockito.Mockito.lenient().when(mapper.updatePreview(any(Long.class), any(String.class))).thenReturn(1);
    }

    private final DraftLedgerEntryMapper mapper = mock(DraftLedgerEntryMapper.class);
    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final QuickEntryService quickEntryService = mock(QuickEntryService.class);
    private final ProductMasterMapper productMasterMapper = mock(ProductMasterMapper.class);
    private final OrderService orderService = mock(OrderService.class);
    private final DraftLedgerEntryService service = new DraftLedgerEntryService(mapper, accountMapper, quickEntryService, new ObjectMapper(), productMasterMapper, orderService, mock(HoldingService.class));

    @Test
    void previewDraftOnlyUpdatesPreviewPayloadAndDoesNotPostLedger() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{\"txnType\":\"EXPENSE\",\"accountId\":7,\"amount\":12.34,\"note\":\"午餐\"}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "生活费账户", "CASH", "SPENDABLE"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertEquals("EXPENSE", preview.getTxnType());
        assertEquals(new BigDecimal("12.34"), preview.getAmount());
        assertEquals("生活费账户", preview.getAccountName());
        assertEquals("CASH", preview.getAccountType());
        assertEquals("SPENDABLE", preview.getFundUsage());
        assertEquals("DECREASE", preview.getImpactDirection());
        assertEquals(new BigDecimal("-12.34"), preview.getAccountDelta());
        assertEquals(true, preview.getWillCreateLedgerTxn());
        assertEquals(false, preview.getWillCreateOrder());
        assertEquals(false, preview.getWillCreateSettlement());
        assertEquals(false, preview.getWillAffectHolding());
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
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "生活费账户", "CASH", "SPENDABLE"));
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
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(account(8L, "工资卡", "BANK", "SPENDABLE"));
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
        assertEquals(false, preview.getWillCreateLedgerTxn());
        assertEquals("NONE", preview.getImpactDirection());
        assertEquals(BigDecimal.ZERO, preview.getAccountDelta());
        assertTrue(preview.getMissingFields().contains("accountId"));
        assertTrue(preview.getMissingFields().contains("amount"));
        verify(accountMapper, never()).selectVisibleRealById(any(), any(), any());
        verify(quickEntryService, never()).quickExpense(any(), any(), any(), any());
        verify(quickEntryService, never()).quickIncome(any(), any(), any(), any());
    }

    @Test
    void previewIncomeDraftReportsPositiveAccountImpact() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{\"txnType\":\"INCOME\",\"accountId\":8,\"amount\":\"88.00\",\"note\":\"奖金\"}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(account(8L, "工资卡", "BANK", "SPENDABLE"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertEquals("INCOME", preview.getTxnType());
        assertEquals("INCREASE", preview.getImpactDirection());
        assertEquals(new BigDecimal("88.00"), preview.getAccountDelta());
        assertEquals(true, preview.getWillCreateLedgerTxn());
        assertEquals(true, preview.getConfirmSupported());
        verify(quickEntryService, never()).quickExpense(any(), any(), any(), any());
        verify(quickEntryService, never()).quickIncome(any(), any(), any(), any());
    }

    @Test
    void previewDraftRejectsInvisibleAccountWithoutPostingLedger() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{\"txnType\":\"EXPENSE\",\"accountId\":999,\"amount\":12.34}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);
        when(accountMapper.selectVisibleRealById(999L, 10L, 20L)).thenReturn(null);

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertEquals(false, preview.getConfirmSupported());
        assertEquals(false, preview.getWillCreateLedgerTxn());
        assertTrue(preview.getMissingFields().contains("accountId"));
        assertTrue(preview.getMessage().contains("不可见"));
        assertTrue(preview.getWarnings().stream().anyMatch(warning -> warning.contains("不可见")));
        verify(quickEntryService, never()).quickExpense(any(), any(), any(), any());
        verify(quickEntryService, never()).quickIncome(any(), any(), any(), any());
    }

    @Test
    void previewDraftRejectsNonRealAccountAsInvisible() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{\"txnType\":\"EXPENSE\",\"accountId\":77,\"amount\":45.67}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);
        when(accountMapper.selectVisibleRealById(77L, 10L, 20L)).thenReturn(null);

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertEquals(false, preview.getConfirmSupported());
        assertEquals(false, preview.getWillCreateLedgerTxn());
        assertTrue(preview.getMissingFields().contains("accountId"));
        assertTrue(preview.getWarnings().stream().anyMatch(warning -> warning.contains("账户不存在")));
        verify(quickEntryService, never()).quickExpense(any(), any(), any(), any());
        verify(quickEntryService, never()).quickIncome(any(), any(), any(), any());
    }

    @Test
    void previewExpenseRejectsReservedAccountWithoutPostingLedger() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{\"txnType\":\"EXPENSE\",\"accountId\":7,\"amount\":12.34}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L))
                .thenReturn(account(7L, "房租专款", "CASH", "RESERVED"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertEquals(false, preview.getConfirmSupported());
        assertTrue(preview.getMessage().contains("RESERVED"));
        assertTrue(preview.getMessage().contains("SPENDABLE"));
        verify(quickEntryService, never()).quickExpense(any(), any(), any(), any());
    }

    @Test
    void confirmExpenseRejectsInvestableAccountWithoutPostingLedger() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{\"txnType\":\"EXPENSE\",\"accountId\":7,\"amount\":12.34}");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L))
                .thenReturn(account(7L, "投资资金", "CASH", "INVESTABLE"));

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> service.confirmDraft(10L, 20L, 1L)
        );

        assertTrue(error.getMessage().contains("INVESTABLE"));
        assertTrue(error.getMessage().contains("SPENDABLE"));
        verify(quickEntryService, never()).quickExpense(any(), any(), any(), any());
        verify(mapper, never()).markConfirmed(any(), any(), any());
    }

    @Test
    void previewDraftRejectsParentAccountWithoutPostingLedger() {
        DraftLedgerEntry draft = draft("DRAFT");
        draft.setParsedPayloadJson("{\"txnType\":\"INCOME\",\"accountId\":7,\"amount\":12.34}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L))
                .thenReturn(account(7L, "现金总账户", "CASH", "SPENDABLE"));
        when(accountMapper.selectChildren(7L))
                .thenReturn(java.util.List.of(account(8L, "工资卡", "CASH", "SPENDABLE")));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertEquals(false, preview.getConfirmSupported());
        assertTrue(preview.getMessage().contains("父账户"));
        assertTrue(preview.getMessage().contains("叶子账户"));
        verify(quickEntryService, never()).quickIncome(any(), any(), any(), any());
    }

    @Test
    void previewUnsupportedTypeKeepsImpactAndLedgerCreationDisabled() {
        DraftLedgerEntry draft = draft("DRAFT");
        // DIVIDEND 等非记账类型仍不支持，用它验证“不支持类型”分支；SELL / REDEMPTION 已在 v0.12 支持。
        draft.setParsedPayloadJson("{\"txnType\":\"DIVIDEND\",\"accountId\":7,\"amount\":12.34}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "证券账户", "BROKER", "INVESTABLE"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertEquals(false, preview.getConfirmSupported());
        assertEquals(false, preview.getWillCreateLedgerTxn());
        assertEquals("NONE", preview.getImpactDirection());
        assertEquals(BigDecimal.ZERO, preview.getAccountDelta());
        assertEquals(false, preview.getWillCreateOrder());
        assertEquals(false, preview.getWillCreateSettlement());
        assertEquals(false, preview.getWillAffectHolding());
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
        // DIVIDEND 等非记账类型仍不支持，用它验证“不支持类型”分支；SELL / REDEMPTION 已在 v0.12 支持。
        draft.setParsedPayloadJson("{\"txnType\":\"DIVIDEND\",\"accountId\":7,\"amount\":12.34}");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "证券账户", "BROKER", "INVESTABLE"));

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.confirmDraft(10L, 20L, 1L));

        assertTrue(error.getMessage().contains("EXPENSE/INCOME/TRANSFER/BUY/SUBSCRIPTION/SELL/REDEMPTION"));
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

    // ==== 草稿创建幂等重放（v0.6 可靠采集前置能力） ====

    @Test
    void createDraftReplayReturnsSameDraftIdForSameUserSource() {
        enableStatefulIdempotencyStore();

        DraftLedgerEntryDTO first = statefulService.createDraft(10L, 20L, createRequest("HERMES_TEXT", "hermes-msg-1"));
        DraftLedgerEntryDTO replay = statefulService.createDraft(10L, 20L, createRequest("HERMES_TEXT", "hermes-msg-1"));

        assertEquals(first.getId(), replay.getId());
        assertEquals("DRAFT", replay.getStatus());
        verify(statefulMapper, times(1)).insert(any());
        verifyNoInteractions(statefulQuickEntryService);
    }

    @Test
    void createDraftWithDifferentSourceRefCreatesSecondDraft() {
        enableStatefulIdempotencyStore();

        DraftLedgerEntryDTO first = statefulService.createDraft(10L, 20L, createRequest("HERMES_TEXT", "hermes-msg-1"));
        DraftLedgerEntryDTO second = statefulService.createDraft(10L, 20L, createRequest("HERMES_TEXT", "hermes-msg-2"));

        assertNotEquals(first.getId(), second.getId());
        verify(statefulMapper, times(2)).insert(any());
    }

    @Test
    void createDraftDoesNotReuseDraftAcrossUserAndFamily() {
        enableStatefulIdempotencyStore();

        DraftLedgerEntryDTO mine = statefulService.createDraft(10L, 20L, createRequest("HERMES_TEXT", "shared-ref"));
        DraftLedgerEntryDTO others = statefulService.createDraft(11L, 21L, createRequest("HERMES_TEXT", "shared-ref"));

        assertNotEquals(mine.getId(), others.getId());
        verify(statefulMapper).selectVisibleBySource(10L, 20L, "HERMES_TEXT", "shared-ref");
        verify(statefulMapper).selectVisibleBySource(11L, 21L, "HERMES_TEXT", "shared-ref");
        verify(statefulMapper, times(2)).insert(any());
    }

    @Test
    void createDraftWithoutSourceRefStaysNonIdempotent() {
        enableStatefulIdempotencyStore();

        DraftLedgerEntryDTO first = statefulService.createDraft(10L, 20L, createRequest("HERMES_TEXT", null));
        DraftLedgerEntryDTO second = statefulService.createDraft(10L, 20L, createRequest("HERMES_TEXT", "   "));

        assertNotEquals(first.getId(), second.getId());
        verify(statefulMapper, never()).selectVisibleBySource(any(), any(), any(), any());
        verify(statefulMapper, times(2)).insert(any());
    }

    @Test
    void createDraftReplaysConfirmedDraftWithoutInsertingNewRow() {
        DraftLedgerEntry existing = draft("CONFIRMED");
        existing.setSourceType("HERMES_TEXT");
        existing.setSourceRef("hermes-msg-9");
        when(mapper.selectVisibleBySource(10L, 20L, "HERMES_TEXT", "hermes-msg-9")).thenReturn(existing);

        DraftLedgerEntryDTO replayed = service.createDraft(10L, 20L, createRequest("HERMES_TEXT", "hermes-msg-9"));

        assertEquals(1L, replayed.getId());
        assertEquals("CONFIRMED", replayed.getStatus());
        verify(mapper, never()).insert(any());
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void createDraftReplaysIgnoredDraftWithoutInsertingNewRow() {
        DraftLedgerEntry existing = draft("IGNORED");
        existing.setSourceType("PAYMENT_NOTIFICATION");
        existing.setSourceRef("candidate-fingerprint-1");
        when(mapper.selectVisibleBySource(10L, 20L, "PAYMENT_NOTIFICATION", "candidate-fingerprint-1")).thenReturn(existing);

        DraftLedgerEntryDTO replayed = service.createDraft(10L, 20L, createRequest("PAYMENT_NOTIFICATION", "candidate-fingerprint-1"));

        assertEquals(1L, replayed.getId());
        assertEquals("IGNORED", replayed.getStatus());
        verify(mapper, never()).insert(any());
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void createDraftDefaultsBlankSourceTypeToManualForIdempotencyKey() {
        DraftLedgerEntry existing = draft("DRAFT");
        existing.setSourceType("manual");
        existing.setSourceRef("ref-manual");
        when(mapper.selectVisibleBySource(10L, 20L, "manual", "ref-manual")).thenReturn(existing);

        DraftLedgerEntryDTO replayed = service.createDraft(10L, 20L, createRequest(null, "ref-manual"));

        assertEquals(1L, replayed.getId());
        verify(mapper, never()).insert(any());
    }

    @Test
    void createDraftInsertsNewRowAndDefaultsSourceTypeWhenNoMatch() {
        when(mapper.selectVisibleBySource(10L, 20L, "manual", "ref-new")).thenReturn(null);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            DraftLedgerEntry inserted = invocation.getArgument(0);
            assertEquals("manual", inserted.getSourceType());
            inserted.setId(5L);
            return 1;
        });
        DraftLedgerEntry persisted = draft("DRAFT");
        persisted.setId(5L);
        when(mapper.selectVisibleById(5L, 10L, 20L)).thenReturn(persisted);

        DraftLedgerEntryDTO created = service.createDraft(10L, 20L, createRequest(null, "ref-new"));

        assertEquals(5L, created.getId());
        verify(mapper).selectVisibleBySource(10L, 20L, "manual", "ref-new");
        verify(mapper).insert(any());
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void transferPreviewRequiresSourceAccount() {
        DraftLedgerEntry transferDraft = draft("DRAFT");
        transferDraft.setParsedPayloadJson("{\"txnType\":\"TRANSFER\",\"targetAccountId\":8,\"amount\":100}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(transferDraft);
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(account(8L, "工资卡", "BANK", "SPENDABLE"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertEquals("TRANSFER", preview.getTxnType());
        assertFalse(preview.getConfirmSupported());
        assertFalse(preview.getWillCreateLedgerTxn());
        assertTrue(preview.getMissingFields().contains("accountId"));
        assertFalse(preview.getMissingFields().contains("targetAccountId"));
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void transferPreviewRequiresTargetAccount() {
        DraftLedgerEntry transferDraft = draft("DRAFT");
        transferDraft.setParsedPayloadJson("{\"txnType\":\"TRANSFER\",\"accountId\":7,\"amount\":100}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(transferDraft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "余额宝", "MMF", "SPENDABLE"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("targetAccountId"));
        assertNull(preview.getTargetAccountId());
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void transferPreviewBlocksIdenticalSourceAndTarget() {
        DraftLedgerEntry transferDraft = draft("DRAFT");
        transferDraft.setParsedPayloadJson("{\"txnType\":\"TRANSFER\",\"accountId\":7,\"targetAccountId\":7,\"amount\":100}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(transferDraft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "余额宝", "MMF", "SPENDABLE"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("targetAccountId"));
        assertTrue(preview.getMessage().contains("不能相同"));
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void transferPreviewBlocksUnavailableOrParentSourceAccount() {
        DraftLedgerEntry transferDraft = draft("DRAFT");
        transferDraft.setParsedPayloadJson("{\"txnType\":\"TRANSFER\",\"accountId\":7,\"targetAccountId\":8,\"amount\":100}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(transferDraft);
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(account(8L, "工资卡", "BANK", "SPENDABLE"));

        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(null);
        DraftPreviewDTO unavailable = service.previewDraft(10L, 20L, 1L);
        assertFalse(unavailable.getConfirmSupported());
        assertTrue(unavailable.getMissingFields().contains("accountId"));

        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "父账户", "BANK", "SPENDABLE"));
        when(accountMapper.selectChildren(7L)).thenReturn(List.of(account(70L, "子账户", "BANK", "SPENDABLE")));
        DraftPreviewDTO parent = service.previewDraft(10L, 20L, 1L);
        assertFalse(parent.getConfirmSupported());
        assertTrue(parent.getMissingFields().contains("accountId"));
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void transferPreviewBlocksUnavailableOrParentTargetAccount() {
        DraftLedgerEntry transferDraft = draft("DRAFT");
        transferDraft.setParsedPayloadJson("{\"txnType\":\"TRANSFER\",\"accountId\":7,\"targetAccountId\":8,\"amount\":100}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(transferDraft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "余额宝", "MMF", "SPENDABLE"));

        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(null);
        DraftPreviewDTO unavailable = service.previewDraft(10L, 20L, 1L);
        assertFalse(unavailable.getConfirmSupported());
        assertTrue(unavailable.getMissingFields().contains("targetAccountId"));

        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(account(8L, "父账户", "BANK", "SPENDABLE"));
        when(accountMapper.selectChildren(8L)).thenReturn(List.of(account(80L, "子账户", "BANK", "SPENDABLE")));
        DraftPreviewDTO parent = service.previewDraft(10L, 20L, 1L);
        assertFalse(parent.getConfirmSupported());
        assertTrue(parent.getMissingFields().contains("targetAccountId"));
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void transferPreviewBlocksDifferentCurrency() {
        DraftLedgerEntry transferDraft = draft("DRAFT");
        transferDraft.setParsedPayloadJson("{\"txnType\":\"TRANSFER\",\"accountId\":7,\"targetAccountId\":8,\"amount\":100}");
        Account usdAccount = account(8L, "美元账户", "BANK", "SPENDABLE");
        usdAccount.setCurrency("USD");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(transferDraft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "余额宝", "MMF", "SPENDABLE"));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(usdAccount);

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("targetAccountId"));
        assertTrue(preview.getMessage().contains("币种"));
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void transferPreviewAllowsReservedToSpendableWithRiskWarning() {
        DraftLedgerEntry transferDraft = draft("DRAFT");
        transferDraft.setParsedPayloadJson("{\"txnType\":\"TRANSFER\",\"accountId\":2,\"targetAccountId\":1,\"amount\":300}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(transferDraft);
        when(accountMapper.selectVisibleRealById(2L, 10L, 20L)).thenReturn(account(2L, "专款账户", "BANK", "RESERVED"));
        when(accountMapper.selectVisibleRealById(1L, 10L, 20L)).thenReturn(account(1L, "日常账户", "CASH", "SPENDABLE"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertEquals(true, preview.getConfirmSupported());
        assertEquals("RESERVED", preview.getFundUsage());
        assertEquals("SPENDABLE", preview.getTargetFundUsage());
        assertEquals(new BigDecimal("-300"), preview.getAccountDelta());
        assertEquals(new BigDecimal("300"), preview.getTargetAccountDelta());
        assertEquals("日常账户", preview.getTargetAccountName());
        assertTrue(preview.getWarnings().stream()
                .anyMatch(warning -> warning.contains("RESERVED") && warning.contains("SPENDABLE")));
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void transferPreviewAllowsInvestableToReservedWithRiskWarning() {
        DraftLedgerEntry transferDraft = draft("DRAFT");
        transferDraft.setParsedPayloadJson("{\"txnType\":\"TRANSFER\",\"accountId\":3,\"targetAccountId\":2,\"amount\":500}");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(transferDraft);
        when(accountMapper.selectVisibleRealById(3L, 10L, 20L)).thenReturn(account(3L, "投资账户", "BANK", "INVESTABLE"));
        when(accountMapper.selectVisibleRealById(2L, 10L, 20L)).thenReturn(account(2L, "专款账户", "BANK", "RESERVED"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertEquals(true, preview.getConfirmSupported());
        assertEquals("INVESTABLE", preview.getFundUsage());
        assertEquals("RESERVED", preview.getTargetFundUsage());
        assertTrue(preview.getWarnings().stream()
                .anyMatch(warning -> warning.contains("INVESTABLE") && warning.contains("RESERVED")));
        verify(mapper).updatePreview(eq(1L), any(String.class));
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void confirmTransferDraftUsesQuickTransferOnceAndMarksConfirmed() {
        DraftLedgerEntry transferDraft = draft("DRAFT");
        transferDraft.setParsedPayloadJson("{\"txnType\":\"TRANSFER\",\"accountId\":7,\"targetAccountId\":8,"
                + "\"amount\":100,\"note\":\"月度资金分区\"}");
        LedgerTxn txn = new LedgerTxn();
        txn.setTxnId("TXN-TRANSFER-1");

        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(transferDraft);
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(transferDraft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "余额宝", "MMF", "SPENDABLE"));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(account(8L, "工资卡", "BANK", "SPENDABLE"));
        when(quickEntryService.quickTransfer(10L, 20L, 7L, 8L, new BigDecimal("100"), "月度资金分区"))
                .thenReturn(txn);
        when(mapper.markConfirmed(1L, "TXN-TRANSFER-1", null)).thenReturn(1);

        service.confirmDraft(10L, 20L, 1L);

        verify(quickEntryService).quickTransfer(10L, 20L, 7L, 8L, new BigDecimal("100"), "月度资金分区");
        verify(mapper).markConfirmed(1L, "TXN-TRANSFER-1", null);
    }

    @Test
    void replayingConfirmedTransferDraftDoesNotCreateSecondTransfer() {
        DraftLedgerEntry confirmed = draft("CONFIRMED");
        confirmed.setParsedPayloadJson("{\"txnType\":\"TRANSFER\",\"accountId\":7,\"targetAccountId\":8,\"amount\":100}");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(confirmed);

        service.confirmDraft(10L, 20L, 1L);

        verifyNoInteractions(quickEntryService);
        verify(mapper, never()).markConfirmed(any(), any(), any());
    }

    @Test
    void transferConfirmStatusUpdateFailureThrowsForRollback() {
        DraftLedgerEntry transferDraft = draft("DRAFT");
        transferDraft.setParsedPayloadJson("{\"txnType\":\"TRANSFER\",\"accountId\":7,\"targetAccountId\":8,\"amount\":100}");
        LedgerTxn txn = new LedgerTxn();
        txn.setTxnId("TXN-TRANSFER-2");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(transferDraft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "余额宝", "MMF", "SPENDABLE"));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(account(8L, "工资卡", "BANK", "SPENDABLE"));
        when(quickEntryService.quickTransfer(10L, 20L, 7L, 8L, new BigDecimal("100"), null)).thenReturn(txn);
        when(mapper.markConfirmed(1L, "TXN-TRANSFER-2", null)).thenReturn(0);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.confirmDraft(10L, 20L, 1L));

        assertTrue(error.getMessage().contains("回滚"));
    }

    @Test
    void confirmTransferDraftRequiresFreshSupportingPreview() {
        DraftLedgerEntry transferDraft = draft("DRAFT");
        transferDraft.setParsedPayloadJson("{\"txnType\":\"TRANSFER\",\"accountId\":7,\"amount\":100}");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(transferDraft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "余额宝", "MMF", "SPENDABLE"));

        assertThrows(RuntimeException.class, () -> service.confirmDraft(10L, 20L, 1L));
        verifyNoInteractions(quickEntryService);
        verify(mapper, never()).markConfirmed(any(), any(), any());
    }
    private DraftLedgerEntryMapper statefulMapper;
    private DraftLedgerEntryService statefulService;
    private QuickEntryService statefulQuickEntryService;

    /**
     * 装配一个内存版草稿 Mapper，让幂等重放测试能观察真实发生的插入次数，而不是只看 mock 调用。
     * 查找条件与 DraftLedgerEntryMapper.xml 的 VisibleCondition 一致：owner_user_id 或 owner_family_id 可见。
     */
    private void enableStatefulIdempotencyStore() {
        List<DraftLedgerEntry> store = new ArrayList<>();
        statefulMapper = mock(DraftLedgerEntryMapper.class);
        statefulQuickEntryService = mock(QuickEntryService.class);
        when(statefulMapper.selectVisibleBySource(any(), any(), any(), any()))
                .thenAnswer(invocation -> visibleBySource(store, invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), invocation.getArgument(3)));
        when(statefulMapper.insert(any())).thenAnswer(invocation -> {
            DraftLedgerEntry inserted = invocation.getArgument(0);
            inserted.setId((long) (store.size() + 1));
            inserted.setStatus("DRAFT");
            store.add(inserted);
            return 1;
        });
        when(statefulMapper.selectVisibleById(any(), any(), any()))
                .thenAnswer(invocation -> visibleById(store, invocation.getArgument(0)));
        statefulService = new DraftLedgerEntryService(statefulMapper, mock(AccountMapper.class), statefulQuickEntryService, new ObjectMapper(), mock(ProductMasterMapper.class), mock(OrderService.class), mock(HoldingService.class));
    }

    private DraftLedgerEntry visibleBySource(List<DraftLedgerEntry> store, Long userId, Long familyId,
                                             String sourceType, String sourceRef) {
        if (sourceRef == null || sourceRef.isBlank()) {
            return null;
        }
        return store.stream()
                .filter(entry -> sourceType.equals(entry.getSourceType()) && sourceRef.equals(entry.getSourceRef()))
                .filter(entry -> entry.getOwnerUserId().equals(userId)
                        || (familyId != null && familyId.equals(entry.getOwnerFamilyId())))
                .findFirst()
                .orElse(null);
    }

    private DraftLedgerEntry visibleById(List<DraftLedgerEntry> store, Long id) {
        return store.stream().filter(entry -> entry.getId().equals(id)).findFirst().orElse(null);
    }

    private CreateDraftRequest createRequest(String sourceType, String sourceRef) {
        CreateDraftRequest request = new CreateDraftRequest();
        request.setSourceType(sourceType);
        request.setSourceRef(sourceRef);
        request.setRawInput("午饭花了32.5，用余额宝生活费");
        request.setParsedPayloadJson("{\"txnType\":\"EXPENSE\",\"amount\":32.5}");
        request.setConfidence(new BigDecimal("0.70"));
        request.setMissingFieldsJson("[\"accountId\"]");
        return request;
    }

    private DraftLedgerEntry draft(String status) {
        DraftLedgerEntry draft = new DraftLedgerEntry();
        draft.setId(1L);
        draft.setOwnerUserId(10L);
        draft.setOwnerFamilyId(20L);
        draft.setStatus(status);
        return draft;
    }

    private Account account(Long id, String name, String type, String fundUsage) {
        Account account = new Account();
        account.setId(id);
        account.setAccountName(name);
        account.setAccountKind("REAL");
        account.setAccountType(type);
        account.setFundUsage(fundUsage);
        account.setOwnerUserId(10L);
        account.setOwnerFamilyId(20L);
        account.setCurrency("CNY");
        account.setIsActive(true);
        return account;
    }
}
