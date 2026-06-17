package com.timelordtty.dca.dto;

import lombok.Data;

/**
 * 今日待办单项 DTO，用于把草稿、结算、建议等候选事项统一展示给前端或 Hermes。
 */
@Data
public class TodoItemDTO {
    /** 待办类型，首版支持 DRAFT，后续可扩展 SETTLEMENT、SUGGESTION。 */
    private String type;
    /** 关联业务记录 ID，DRAFT 类型对应草稿 ID。 */
    private String refId;
    /** 人类可读标题。 */
    private String title;
    /** 待办摘要说明，前端可直接展示。 */
    private String description;
    /** 当前业务状态，例如 DRAFT。 */
    private String status;
    /** 前端动作路径，只用于跳转，不代表自动执行。 */
    private String actionPath;
}
