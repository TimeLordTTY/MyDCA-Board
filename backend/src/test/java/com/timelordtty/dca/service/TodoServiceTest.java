package com.timelordtty.dca.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.timelordtty.dca.dto.TodayTodoDTO;
import com.timelordtty.dca.mapper.DraftLedgerEntryMapper;
import com.timelordtty.dca.model.DraftLedgerEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证今日待办首版只读聚合 DRAFT 草稿，不触发正式入账动作。
 */
class TodoServiceTest {
    private final DraftLedgerEntryMapper draftLedgerEntryMapper = mock(DraftLedgerEntryMapper.class);
    private final QuickEntryService quickEntryService = mock(QuickEntryService.class);
    private final TodoService todoService = new TodoService(draftLedgerEntryMapper);

    @Test
    void getTodayTodosCountsVisibleDraftsOnly() {
        DraftLedgerEntry draft = draft(101L, "DRAFT", "午饭 32.5");
        when(draftLedgerEntryMapper.countVisibleByStatus(10L, 20L, "DRAFT")).thenReturn(1);
        when(draftLedgerEntryMapper.selectVisibleList(10L, 20L, "DRAFT", 0, 20)).thenReturn(List.of(draft));

        TodayTodoDTO todos = todoService.getTodayTodos(10L, 20L);

        assertEquals(1, todos.getTotalCount());
        assertEquals(1, todos.getDraftCount());
        assertEquals(0, todos.getSettlementCount());
        assertEquals(0, todos.getSuggestionCount());
        assertEquals(1, todos.getItems().size());
        assertEquals("DRAFT", todos.getItems().get(0).getType());
        assertEquals("101", todos.getItems().get(0).getRefId());
        assertEquals("/drafts?draftId=101", todos.getItems().get(0).getActionPath());
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void getTodayTodosDoesNotCountConfirmedOrIgnoredDrafts() {
        when(draftLedgerEntryMapper.countVisibleByStatus(10L, null, "DRAFT")).thenReturn(0);
        when(draftLedgerEntryMapper.selectVisibleList(10L, null, "DRAFT", 0, 20)).thenReturn(List.of());

        TodayTodoDTO todos = todoService.getTodayTodos(10L, null);

        assertEquals(0, todos.getTotalCount());
        assertTrue(todos.getItems().isEmpty());
    }

    @Test
    void getTodayTodosUsesVisibleDraftMapperBoundary() {
        todoService.getTodayTodos(10L, 20L);

        verify(draftLedgerEntryMapper).countVisibleByStatus(10L, 20L, "DRAFT");
        verify(draftLedgerEntryMapper).selectVisibleList(10L, 20L, "DRAFT", 0, 20);
    }

    @Test
    void getTodayTodosLimitsItemsButKeepsRealDraftCount() {
        List<DraftLedgerEntry> visibleDrafts = java.util.stream.LongStream.rangeClosed(1, 20)
                .mapToObj(id -> draft(id, "DRAFT", "草稿 " + id))
                .toList();
        when(draftLedgerEntryMapper.countVisibleByStatus(10L, 20L, "DRAFT")).thenReturn(25);
        when(draftLedgerEntryMapper.selectVisibleList(10L, 20L, "DRAFT", 0, 20)).thenReturn(visibleDrafts);

        TodayTodoDTO todos = todoService.getTodayTodos(10L, 20L);

        assertEquals(25, todos.getDraftCount());
        assertEquals(25, todos.getTotalCount());
        assertEquals(20, todos.getItems().size());
        verify(draftLedgerEntryMapper, times(1)).selectVisibleList(10L, 20L, "DRAFT", 0, 20);
        verifyNoInteractions(quickEntryService);
    }

    private DraftLedgerEntry draft(Long id, String status, String rawInput) {
        DraftLedgerEntry draft = new DraftLedgerEntry();
        draft.setId(id);
        draft.setOwnerUserId(10L);
        draft.setOwnerFamilyId(20L);
        draft.setStatus(status);
        draft.setRawInput(rawInput);
        draft.setSourceType("manual");
        return draft;
    }
}
