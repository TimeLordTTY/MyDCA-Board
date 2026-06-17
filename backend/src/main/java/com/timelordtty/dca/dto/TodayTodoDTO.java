package com.timelordtty.dca.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日待办聚合 DTO，只读展示当前用户需要处理的候选事项。
 */
@Data
public class TodayTodoDTO {
    /** 当前待办日期，使用服务端本地日期。 */
    private LocalDate date;
    /** 待办总数。 */
    private Integer totalCount;
    /** 待确认草稿数量。 */
    private Integer draftCount;
    /** 待结算数量；首版暂未接入时为 0。 */
    private Integer settlementCount;
    /** 策略建议数量；Phase4 未实现时为 0。 */
    private Integer suggestionCount;
    /** 待办明细列表，首版主要包含 DRAFT 草稿。 */
    private List<TodoItemDTO> items;
}
