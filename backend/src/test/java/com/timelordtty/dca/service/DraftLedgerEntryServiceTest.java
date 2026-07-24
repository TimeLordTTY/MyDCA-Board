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
import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.DraftLedgerEntryMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.DraftLedgerEntry;
import com.timelordtty.dca.model.LedgerTxn;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 验证 Phase3 草稿流水服务的安全边界：草稿不进正式账本，确认必须走统一记账入口。
 */
class DraftLedgerEntryServiceTest {

    private final DraftLedgerEntryMapper mapper = mock(DraftLedgerEntryMapper.class);
    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final QuickEntryService quickEntryService = mock(QuickEntryService.class);
    private final DraftLedgerEntryService service = new DraftLedgerEntryService(mapper, accountMapper, quickEntryService, new ObjectMapper());

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
        draft.setParsedPayloadJson("{\"txnType\":\"BUY\",\"accountId\":7,\"amount\":12.34}");
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
        draft.setParsedPayloadJson("{\"txnType\":\"BUY\",\"accountId\":7,\"amount\":12.34}");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account(7L, "证券账户", "BROKER", "INVESTABLE"));

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
