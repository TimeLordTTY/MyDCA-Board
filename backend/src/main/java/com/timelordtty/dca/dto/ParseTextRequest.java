package com.timelordtty.dca.dto;

import lombok.Data;

/**
 * 文本记账解析请求，首版用于 Hermes 或前端把自然语言输入交给后端生成待确认草稿意图。
 */
@Data
public class ParseTextRequest {
    /** 原始自然语言文本，例如“午饭花了32.5，用余额宝生活费”。 */
    private String text;
    /** 外部来源引用，例如 Hermes 消息 ID，仅用于追踪来源，不参与正式入账。 */
    private String sourceRef;
}
