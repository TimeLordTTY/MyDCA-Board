package com.timelordtty.dca.dto;

import lombok.Data;

/**
 * 根据已解析 intent 创建草稿的请求；服务端仍会强制使用当前登录用户作为归属边界。
 */
@Data
public class DraftFromIntentRequest {
    /** 由 parse-text 或外部安全解析器生成的候选记账意图。 */
    private AccountingIntentDTO intent;
}
