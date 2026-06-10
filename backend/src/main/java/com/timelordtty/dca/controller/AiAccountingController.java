package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AccountingIntentDTO;
import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.DraftFromIntentRequest;
import com.timelordtty.dca.dto.DraftFromIntentResponse;
import com.timelordtty.dca.dto.ParseTextRequest;
import com.timelordtty.dca.service.AiAccountingService;
import com.timelordtty.dca.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase3 AI 记账控制器，只提供文本解析和草稿生成入口，不提供自动确认入账能力。
 */
@RestController
@RequestMapping("/api/v2/ai/accounting")
public class AiAccountingController {
    /** 文本记账解析服务，首版使用规则解析，不调用真实大模型。 */
    private final AiAccountingService aiAccountingService;
    /** 当前登录用户服务，用于强制绑定 ownerUserId / ownerFamilyId。 */
    private final UserService userService;

    public AiAccountingController(AiAccountingService aiAccountingService, UserService userService) {
        this.aiAccountingService = aiAccountingService;
        this.userService = userService;
    }

    /**
     * 将自然语言文本解析成候选记账意图；结果不会写入正式账本。
     */
    @PostMapping("/parse-text")
    public ResponseEntity<AccountingIntentDTO> parseText(@RequestBody ParseTextRequest request) {
        return ResponseEntity.ok(aiAccountingService.parseText(request));
    }

    /**
     * 根据候选 intent 创建 DRAFT 草稿；正式入账仍必须由用户调用草稿确认接口。
     */
    @PostMapping("/draft-from-intent")
    public ResponseEntity<DraftFromIntentResponse> draftFromIntent(@RequestBody DraftFromIntentRequest request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(aiAccountingService.draftFromIntent(currentUser.getId(), currentUser.getFamilyId(), request));
    }
}
