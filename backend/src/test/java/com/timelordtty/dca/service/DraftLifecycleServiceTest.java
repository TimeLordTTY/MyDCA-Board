package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.CreateDraftRequest;
import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.DraftLedgerEntryMapper;
import com.timelordtty.dca.mapper.DraftLifecycleEventMapper;
import com.timelordtty.dca.mapper.ProductMasterMapper;
import com.timelordtty.dca.model.DraftLedgerEntry;
import com.timelordtty.dca.model.DraftLifecycleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DraftLifecycleServiceTest {
    private final DraftLedgerEntryMapper drafts = mock(DraftLedgerEntryMapper.class);
    private final DraftLifecycleEventMapper events = mock(DraftLifecycleEventMapper.class);
    private final DraftLedgerEntryService service = new DraftLedgerEntryService(drafts, mock(AccountMapper.class),
            mock(QuickEntryService.class), new ObjectMapper(), mock(ProductMasterMapper.class),
            mock(OrderService.class), mock(HoldingService.class));

    @BeforeEach void setup() { ReflectionTestUtils.setField(service, "lifecycleEvents", events); }

    private DraftLedgerEntry draft(String status) {
        DraftLedgerEntry draft = new DraftLedgerEntry();
        draft.setId(5L);
        draft.setOwnerUserId(10L);
        draft.setOwnerFamilyId(20L);
        draft.setSourceType("ocr");
        draft.setSourceRef("ref-1");
        draft.setStatus(status);
        return draft;
    }

    @Test void historyRequiresVisibleDraft() {
        assertThrows(RuntimeException.class, () -> service.history(11L, 21L, 5L));
        verifyNoInteractions(events);
    }

    @Test void reopenIgnoredClearsPreviewAndWritesOneEvent() {
        DraftLedgerEntry ignored = draft("IGNORED");
        when(drafts.selectVisibleByIdForUpdate(5L, 10L, 20L)).thenReturn(ignored);
        when(drafts.selectVisibleBySource(10L, 20L, "ocr", "ref-1")).thenReturn(ignored);
        when(drafts.reopenIgnored(5L)).thenReturn(1);
        when(drafts.selectVisibleById(5L, 10L, 20L)).thenReturn(draft("DRAFT"));
        assertEquals("DRAFT", service.reopen(10L, 20L, 5L).getStatus());
        verify(events).insert(argThat(event -> "reopen".equals(event.getEventType())
                && "IGNORED".equals(event.getStatusBefore()) && "DRAFT".equals(event.getStatusAfter())));
    }

    @Test void confirmedCannotReopen() {
        when(drafts.selectVisibleByIdForUpdate(5L, 10L, 20L)).thenReturn(draft("CONFIRMED"));
        assertThrows(RuntimeException.class, () -> service.reopen(10L, 20L, 5L));
        verify(drafts, never()).reopenIgnored(anyLong());
        verifyNoInteractions(events);
    }

    @Test void repeatedReopenIsIdempotent() {
        when(drafts.selectVisibleByIdForUpdate(5L, 10L, 20L)).thenReturn(draft("DRAFT"));
        assertEquals("DRAFT", service.reopen(10L, 20L, 5L).getStatus());
        verify(drafts, never()).reopenIgnored(anyLong());
        verifyNoInteractions(events);
    }

    @Test void copyUsesNewSourceAndNeverConfirms() {
        DraftLedgerEntry original = draft("CONFIRMED");
        original.setRawInput("支付通知原文");
        java.util.concurrent.atomic.AtomicReference<String> newSource = new java.util.concurrent.atomic.AtomicReference<>();
        when(drafts.selectVisibleByIdForUpdate(5L, 10L, 20L)).thenReturn(original);
        when(drafts.selectVisibleById(anyLong(), eq(10L), eq(20L))).thenAnswer(invocation -> {
            DraftLedgerEntry created = draft("DRAFT");
            created.setId(invocation.getArgument(0));
            created.setSourceRef(newSource.get());
            return created;
        });
        doAnswer(invocation -> {
            DraftLedgerEntry created = invocation.getArgument(0);
            created.setId(6L);
            newSource.set(created.getSourceRef());
            return 1;
        }).when(drafts).insert(any());
        var result = service.copyConfirmed(10L, 20L, 5L);
        assertEquals(6L, result.getId());
        verify(drafts).insert(argThat(created -> created.getSourceRef().startsWith("copy-")
                && !"ref-1".equals(created.getSourceRef()) && created.getConfirmTxnId() == null));
        verify(drafts, never()).markConfirmed(anyLong(), any(), any());
    }

    @Test void failedConfirmRecordsAttemptAndFailureWithoutConfirmedEvent() {
        DraftFailureAuditService failureAudit = mock(DraftFailureAuditService.class);
        ReflectionTestUtils.setField(service, "failureAudit", failureAudit);
        when(drafts.selectVisibleByIdForUpdate(5L, 10L, 20L)).thenReturn(draft("DRAFT"));
        assertThrows(RuntimeException.class, () -> service.confirmDraft(10L, 20L, 5L));
        verify(failureAudit).write(5L, 10L, "ocr", "confirm_attempt");
        verify(failureAudit).write(5L, 10L, "ocr", "confirm_failed");
        verify(events, never()).insert(any(DraftLifecycleEvent.class));
        verify(drafts, never()).markConfirmed(anyLong(), any(), any());
    }
}
