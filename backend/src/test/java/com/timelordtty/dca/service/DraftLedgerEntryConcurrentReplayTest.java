package com.timelordtty.dca.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.CreateDraftRequest;
import com.timelordtty.dca.dto.DraftLedgerEntryDTO;
import com.timelordtty.dca.dto.UpdateDraftRequest;
import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.DraftLedgerEntryMapper;
import com.timelordtty.dca.model.DraftLedgerEntry;
import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

/**
 * v0.8 草稿强幂等：验证“应用层先查后插 + 数据库唯一键 + 唯一冲突恢复”的并发重放安全边界。
 *
 * <p>测试不连接任何数据库：一方面用 Mockito 直接模拟 {@link DuplicateKeyException} 与其他 DataAccessException，
 * 另一方面用内存夹具 {@link DraftSourceUniqueStore} 复现步骤 03 两个唯一键的判定规则与草稿可见性，
 * 从而在单元测试里真实观察插入次数与恢复结果，而不是只看 mock 调用次数。</p>
 *
 * <p>所有用例都必须保持：不 preview、不 confirm、不调用 QuickEntryService、不写正式流水 / 订单 / 结算 / 持仓。</p>
 */
class DraftLedgerEntryConcurrentReplayTest {

    private final DraftLedgerEntryMapper mapper = mock(DraftLedgerEntryMapper.class);
    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final QuickEntryService quickEntryService = mock(QuickEntryService.class);
    private final DraftLedgerEntryService service =
            new DraftLedgerEntryService(mapper, accountMapper, quickEntryService, new ObjectMapper());

    @Test
    void concurrentReplayInSameUserScopeKeepsSingleDraftAndReturnsExistingOne() {
        DraftSourceUniqueStore store = new DraftSourceUniqueStore();
        bindStore(store);
        DraftLedgerEntry first = store.insert(newDraft(10L, 20L, "HERMES_TEXT", "hermes-msg-1"));
        store.missNextSourceLookup();

        DraftLedgerEntryDTO replay = service.createDraft(10L, 20L, createRequest("HERMES_TEXT", "hermes-msg-1"));

        assertEquals(first.getId(), replay.getId());
        assertEquals("DRAFT", replay.getStatus());
        assertEquals(1, store.snapshot().size());
        verify(mapper, times(1)).insert(any());
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void concurrentReplayInSameFamilyScopeKeepsSingleDraftAndReturnsExistingOne() {
        DraftSourceUniqueStore store = new DraftSourceUniqueStore();
        bindStore(store);
        DraftLedgerEntry createdByFamilyMember = store.insert(newDraft(11L, 20L, "PAYMENT_NOTIFICATION", "fingerprint-1"));
        store.missNextSourceLookup();

        DraftLedgerEntryDTO replay = service.createDraft(12L, 20L, createRequest("PAYMENT_NOTIFICATION", "fingerprint-1"));

        assertEquals(createdByFamilyMember.getId(), replay.getId());
        assertEquals(11L, replay.getOwnerUserId());
        assertEquals(1, store.snapshot().size());
        verify(mapper, times(1)).insert(any());
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void sameSourceRefIsAllowedForDifferentOwnerScopes() {
        DraftSourceUniqueStore store = new DraftSourceUniqueStore();
        bindStore(store);

        DraftLedgerEntryDTO mine = service.createDraft(10L, 20L, createRequest("HERMES_TEXT", "shared-ref"));
        DraftLedgerEntryDTO otherFamily = service.createDraft(11L, 21L, createRequest("HERMES_TEXT", "shared-ref"));
        DraftLedgerEntryDTO noFamily = service.createDraft(12L, null, createRequest("HERMES_TEXT", "shared-ref"));

        assertNotEquals(mine.getId(), otherFamily.getId());
        assertNotEquals(mine.getId(), noFamily.getId());
        assertEquals(3, store.snapshot().size());
    }

    @Test
    void blankSourceRefStaysNonIdempotentAndIsStoredAsNull() {
        DraftSourceUniqueStore store = new DraftSourceUniqueStore();
        bindStore(store);

        DraftLedgerEntryDTO first = service.createDraft(10L, 20L, createRequest("HERMES_TEXT", null));
        DraftLedgerEntryDTO second = service.createDraft(10L, 20L, createRequest("HERMES_TEXT", "   "));
        DraftLedgerEntryDTO third = service.createDraft(10L, 20L, createRequest("HERMES_TEXT", null));

        assertNotEquals(first.getId(), second.getId());
        assertNotEquals(second.getId(), third.getId());
        assertEquals(3, store.snapshot().size());
        store.snapshot().forEach(row -> assertNull(row.getSourceRef()));
    }

    @Test
    void duplicateKeyRecoveryReturnsExistingConfirmedDraftWithoutPosting() {
        DraftSourceUniqueStore store = new DraftSourceUniqueStore();
        bindStore(store);
        DraftLedgerEntry confirmed = newDraft(10L, 20L, "HERMES_TEXT", "hermes-msg-9");
        confirmed.setStatus("CONFIRMED");
        confirmed.setConfirmTxnId("TXN-DONE");
        store.insert(confirmed);
        store.missNextSourceLookup();

        DraftLedgerEntryDTO replay = service.createDraft(10L, 20L, createRequest("HERMES_TEXT", "hermes-msg-9"));

        assertEquals(confirmed.getId(), replay.getId());
        assertEquals("CONFIRMED", replay.getStatus());
        assertEquals("TXN-DONE", replay.getConfirmTxnId());
        verify(mapper, times(1)).insert(any());
        verify(mapper, never()).markConfirmed(any(), any(), any());
        verify(mapper, never()).updatePreview(any(), any());
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void duplicateKeyRecoveryReturnsExistingIgnoredDraftWithoutPosting() {
        DraftSourceUniqueStore store = new DraftSourceUniqueStore();
        bindStore(store);
        DraftLedgerEntry ignored = newDraft(10L, 20L, "PAYMENT_NOTIFICATION", "fingerprint-ignored");
        ignored.setStatus("IGNORED");
        ignored.setIgnoreReason("重复的支付通知");
        store.insert(ignored);
        store.missNextSourceLookup();

        DraftLedgerEntryDTO replay =
                service.createDraft(10L, 20L, createRequest("PAYMENT_NOTIFICATION", "fingerprint-ignored"));

        assertEquals(ignored.getId(), replay.getId());
        assertEquals("IGNORED", replay.getStatus());
        assertEquals("重复的支付通知", replay.getIgnoreReason());
        verify(mapper, times(1)).insert(any());
        verify(mapper, never()).markIgnored(any(), any());
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void duplicateKeyWithoutVisibleExistingDraftRethrowsTheOriginalConflict() {
        DuplicateKeyException conflict = duplicateKey();
        when(mapper.selectVisibleBySource(any(), any(), any(), any())).thenReturn(null);
        when(mapper.insert(any())).thenThrow(conflict);

        DuplicateKeyException thrown = assertThrows(DuplicateKeyException.class,
                () -> service.createDraft(10L, 20L, createRequest("HERMES_TEXT", "hermes-msg-1")));

        assertSame(conflict, thrown);
        // 预查 1 次 + 冲突后重查 1 次：重查不到既有草稿时必须原样抛出，不能伪装成成功。
        verify(mapper, times(2)).selectVisibleBySource(any(), any(), any(), any());
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void nonDuplicateDatabaseFailureIsStillThrownAndSkipsRecoveryLookup() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("数据库连接中断");
        when(mapper.selectVisibleBySource(any(), any(), any(), any())).thenReturn(null);
        when(mapper.insert(any())).thenThrow(failure);

        DataAccessResourceFailureException thrown = assertThrows(DataAccessResourceFailureException.class,
                () -> service.createDraft(10L, 20L, createRequest("HERMES_TEXT", "hermes-msg-1")));

        assertSame(failure, thrown);
        // 只有唯一键冲突才允许走到恢复重查；其他数据库异常必须直接抛出。
        verify(mapper, times(1)).selectVisibleBySource(any(), any(), any(), any());
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void updateDraftReportsSourceRefCollisionAsBusinessError() {
        DraftLedgerEntry existing = newDraft(10L, 20L, "manual", "ref-a");
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(existing);
        when(mapper.updateDraftContent(any())).thenThrow(duplicateKey());

        UpdateDraftRequest request = new UpdateDraftRequest();
        request.setRawInput("改成新内容");
        request.setSourceRef("ref-b");

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.updateDraft(10L, 20L, 1L, request));

        assertFalse(error instanceof DuplicateKeyException);
        assertTrue(error.getMessage().contains("占用"));
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void updateDraftNormalizesBlankSourceRefToNull() {
        DraftLedgerEntry existing = newDraft(10L, 20L, "manual", "ref-a");
        existing.setId(1L);
        existing.setStatus("DRAFT");
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(existing);
        ArgumentCaptor<DraftLedgerEntry> captor = ArgumentCaptor.forClass(DraftLedgerEntry.class);
        when(mapper.updateDraftContent(captor.capture())).thenReturn(1);

        UpdateDraftRequest request = new UpdateDraftRequest();
        request.setRawInput("改成新内容");
        request.setSourceRef("   ");

        service.updateDraft(10L, 20L, 1L, request);

        assertNull(captor.getValue().getSourceRef());
    }

    private void bindStore(DraftSourceUniqueStore store) {
        when(mapper.insert(any())).thenAnswer(invocation -> {
            store.insert(invocation.getArgument(0));
            return 1;
        });
        when(mapper.selectVisibleBySource(any(), any(), any(), any()))
                .thenAnswer(invocation -> store.selectVisibleBySource(
                        invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), invocation.getArgument(3)));
        when(mapper.selectVisibleById(any(), any(), any()))
                .thenAnswer(invocation -> store.selectVisibleById(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    }

    private DuplicateKeyException duplicateKey() {
        return new DuplicateKeyException(
                "Duplicate entry for key 'uk_draft_ledger_user_source'",
                new SQLIntegrityConstraintViolationException("Duplicate entry"));
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

    private DraftLedgerEntry newDraft(Long ownerUserId, Long ownerFamilyId, String sourceType, String sourceRef) {
        DraftLedgerEntry draft = new DraftLedgerEntry();
        draft.setOwnerUserId(ownerUserId);
        draft.setOwnerFamilyId(ownerFamilyId);
        draft.setSourceType(sourceType);
        draft.setSourceRef(sourceRef);
        draft.setStatus("DRAFT");
        return draft;
    }

    /**
     * 内存版 draft_ledger_entry 夹具。
     *
     * <p>冲突判定完全对齐步骤 03 的两个唯一键：同一个人或同一个家庭 + 同 source_type + 同 source_ref 即冲突；
     * 任一唯一键列为 NULL 时该唯一键不参与判定（MySQL 唯一索引不约束多行 NULL）。
     * 可见性判定对齐 DraftLedgerEntryMapper.xml 的 VisibleCondition：owner_user_id = :userId OR owner_family_id = :familyId。</p>
     */
    private static final class DraftSourceUniqueStore {
        private final List<DraftLedgerEntry> rows = new ArrayList<>();
        private long sequence = 0L;
        private boolean missNextSourceLookup = false;

        /** 模拟并发场景：对方刚提交，而本请求的幂等预查还没有看到它。 */
        void missNextSourceLookup() {
            this.missNextSourceLookup = true;
        }

        List<DraftLedgerEntry> snapshot() {
            return List.copyOf(rows);
        }

        DraftLedgerEntry insert(DraftLedgerEntry candidate) {
            boolean conflict = rows.stream().anyMatch(row -> conflicts(row, candidate));
            if (conflict) {
                throw new DuplicateKeyException(
                        "Duplicate entry " + candidate.getSourceRef() + " for key 'uk_draft_ledger_source'",
                        new SQLIntegrityConstraintViolationException("Duplicate entry"));
            }
            candidate.setId(++sequence);
            candidate.setStatus(candidate.getStatus() == null ? "DRAFT" : candidate.getStatus());
            rows.add(candidate);
            return candidate;
        }

        DraftLedgerEntry selectVisibleBySource(Long userId, Long familyId, String sourceType, String sourceRef) {
            if (missNextSourceLookup) {
                missNextSourceLookup = false;
                return null;
            }
            if (sourceRef == null || sourceRef.isBlank()) {
                return null;
            }
            return rows.stream()
                    .filter(row -> Objects.equals(sourceType, row.getSourceType()))
                    .filter(row -> sourceRef.equals(row.getSourceRef()))
                    .filter(row -> isVisible(row, userId, familyId))
                    .min(Comparator.comparing(DraftLedgerEntry::getId))
                    .orElse(null);
        }

        DraftLedgerEntry selectVisibleById(Long id, Long userId, Long familyId) {
            return rows.stream()
                    .filter(row -> Objects.equals(id, row.getId()))
                    .filter(row -> isVisible(row, userId, familyId))
                    .findFirst()
                    .orElse(null);
        }

        private boolean isVisible(DraftLedgerEntry row, Long userId, Long familyId) {
            return Objects.equals(row.getOwnerUserId(), userId)
                    || (familyId != null && Objects.equals(row.getOwnerFamilyId(), familyId));
        }

        private boolean conflicts(DraftLedgerEntry existing, DraftLedgerEntry candidate) {
            if (existing.getSourceRef() == null || candidate.getSourceRef() == null) {
                return false;
            }
            if (!Objects.equals(existing.getSourceType(), candidate.getSourceType())
                    || !Objects.equals(existing.getSourceRef(), candidate.getSourceRef())) {
                return false;
            }
            if (Objects.equals(existing.getOwnerUserId(), candidate.getOwnerUserId())) {
                return true;
            }
            return candidate.getOwnerFamilyId() != null
                    && Objects.equals(existing.getOwnerFamilyId(), candidate.getOwnerFamilyId());
        }
    }
}