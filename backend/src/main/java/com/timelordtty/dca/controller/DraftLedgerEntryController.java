package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.CreateDraftRequest;
import com.timelordtty.dca.dto.DraftLedgerEntryDTO;
import com.timelordtty.dca.dto.DraftPreviewDTO;
import com.timelordtty.dca.dto.IgnoreDraftRequest;
import com.timelordtty.dca.dto.UpdateDraftRequest;
import com.timelordtty.dca.service.DraftLedgerEntryService;
import com.timelordtty.dca.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase3 草稿流水控制器，提供候选记账草稿的创建、查询、预览、确认和忽略 API。
 *
 * <p>除确认接口委托服务层走 QuickEntryService 外，其余接口只操作草稿表，不影响正式账本。</p>
 */
@RestController
@RequestMapping("/api/v2/drafts")
public class DraftLedgerEntryController {
    /** 草稿流水服务，封装草稿状态机和确认安全边界。 */
    private final DraftLedgerEntryService draftLedgerEntryService;
    /** 当前登录用户服务，用于获取用户 ID 与家庭 ID 作为草稿权限边界。 */
    private final UserService userService;

    /**
     * 装配草稿服务和用户服务。
     */
    public DraftLedgerEntryController(DraftLedgerEntryService draftLedgerEntryService, UserService userService) {
        this.draftLedgerEntryService = draftLedgerEntryService;
        this.userService = userService;
    }

    /**
     * 创建草稿候选记录，初始状态为 DRAFT，不生成正式流水。
     */
    @PostMapping
    public ResponseEntity<DraftLedgerEntryDTO> createDraft(@RequestBody CreateDraftRequest request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(draftLedgerEntryService.createDraft(currentUser.getId(), currentUser.getFamilyId(), request));
    }

    /**
     * 查询当前用户或家庭可见草稿列表，支持按状态过滤和分页。
     */
    @GetMapping
    public ResponseEntity<List<DraftLedgerEntryDTO>> listDrafts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(draftLedgerEntryService.listDrafts(
                currentUser.getId(), currentUser.getFamilyId(), status, page, pageSize));
    }

    /**
     * 查询单条草稿详情，只允许访问本人或同家庭可见草稿。
     */
    @GetMapping("/{draftId}")
    public ResponseEntity<DraftLedgerEntryDTO> getDraft(@PathVariable Long draftId) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(draftLedgerEntryService.getDraft(currentUser.getId(), currentUser.getFamilyId(), draftId));
    }

    /**
     * 更新 DRAFT 状态草稿候选内容，已确认或已忽略草稿不可修改。
     */
    @PutMapping("/{draftId}")
    public ResponseEntity<DraftLedgerEntryDTO> updateDraft(@PathVariable Long draftId,
                                                           @RequestBody UpdateDraftRequest request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(draftLedgerEntryService.updateDraft(
                currentUser.getId(), currentUser.getFamilyId(), draftId, request));
    }

    /**
     * 生成草稿确认预览，只写 preview_payload_json，不创建正式流水。
     */
    @PostMapping("/{draftId}/preview")
    public ResponseEntity<DraftPreviewDTO> previewDraft(@PathVariable Long draftId) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(draftLedgerEntryService.previewDraft(currentUser.getId(), currentUser.getFamilyId(), draftId));
    }

    /**
     * 确认草稿；首版仅支持 EXPENSE/INCOME，并且必须走 QuickEntryService 统一记账。
     */
    @PostMapping("/{draftId}/confirm")
    public ResponseEntity<DraftLedgerEntryDTO> confirmDraft(@PathVariable Long draftId) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(draftLedgerEntryService.confirmDraft(currentUser.getId(), currentUser.getFamilyId(), draftId));
    }

    /**
     * 忽略草稿，记录原因后不再允许确认或编辑。
     */
    @PostMapping("/{draftId}/ignore")
    public ResponseEntity<DraftLedgerEntryDTO> ignoreDraft(@PathVariable Long draftId,
                                                           @RequestBody(required = false) IgnoreDraftRequest request) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        String reason = request == null ? null : request.getIgnoreReason();
        return ResponseEntity.ok(draftLedgerEntryService.ignoreDraft(
                currentUser.getId(), currentUser.getFamilyId(), draftId, reason));
    }
}
