package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.TodayTodoDTO;
import com.timelordtty.dca.dto.TodoItemDTO;
import com.timelordtty.dca.mapper.DraftLedgerEntryMapper;
import com.timelordtty.dca.model.DraftLedgerEntry;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日待办聚合服务，首版只读汇总待确认草稿，不写正式账本或触发确认动作。
 */
@Service
public class TodoService {
    private static final int DRAFT_ITEM_LIMIT = 20;

    /** 草稿只读查询入口，复用现有用户/家庭可见性条件。 */
    private final DraftLedgerEntryMapper draftLedgerEntryMapper;

    public TodoService(DraftLedgerEntryMapper draftLedgerEntryMapper) {
        this.draftLedgerEntryMapper = draftLedgerEntryMapper;
    }

    /**
     * 查询当前用户或家庭可见的今日待办摘要。
     */
    public TodayTodoDTO getTodayTodos(Long userId, Long familyId) {
        int draftCount = draftLedgerEntryMapper.countVisibleByStatus(userId, familyId, "DRAFT");
        List<TodoItemDTO> draftItems = draftLedgerEntryMapper
                .selectVisibleList(userId, familyId, "DRAFT", 0, DRAFT_ITEM_LIMIT)
                .stream()
                .map(this::toDraftTodoItem)
                .toList();

        TodayTodoDTO dto = new TodayTodoDTO();
        dto.setDate(LocalDate.now());
        dto.setDraftCount(draftCount);
        dto.setSettlementCount(0);
        dto.setSuggestionCount(0);
        dto.setTotalCount(draftCount);
        dto.setItems(draftItems);
        return dto;
    }

    private TodoItemDTO toDraftTodoItem(DraftLedgerEntry draft) {
        TodoItemDTO item = new TodoItemDTO();
        item.setType("DRAFT");
        item.setRefId(String.valueOf(draft.getId()));
        item.setTitle("待确认草稿 #" + draft.getId());
        item.setDescription(buildDraftDescription(draft));
        item.setStatus(draft.getStatus());
        item.setActionPath("/drafts?draftId=" + draft.getId());
        return item;
    }

    private String buildDraftDescription(DraftLedgerEntry draft) {
        if (draft.getRawInput() != null && !draft.getRawInput().isBlank()) {
            return draft.getRawInput();
        }
        if (draft.getSourceType() != null && !draft.getSourceType().isBlank()) {
            return "来源：" + draft.getSourceType();
        }
        return "请打开草稿箱补齐并预览后再确认。";
    }
}
