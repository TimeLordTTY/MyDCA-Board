package com.timelordtty.dca.dto;

import lombok.Data;

/**
 * intent 转草稿响应，返回标准化 intent 和新建的 DRAFT 草稿。
 */
@Data
public class DraftFromIntentResponse {
    /** 本次用于创建草稿的标准化 intent。 */
    private AccountingIntentDTO intent;
    /** 新建的草稿流水候选，状态必须仍为 DRAFT。 */
    private DraftLedgerEntryDTO draft;
}
