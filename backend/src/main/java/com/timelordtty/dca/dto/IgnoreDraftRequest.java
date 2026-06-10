package com.timelordtty.dca.dto;

import lombok.Data;

/**
 * 忽略草稿流水请求，用于记录人工放弃确认该候选记账的原因。
 */
@Data
public class IgnoreDraftRequest {
    /** 忽略原因，例如重复识别、非账本事件、信息错误等。 */
    private String ignoreReason;
}
