package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.service.HoldingService;
import com.timelordtty.dca.service.FamilyService;
import com.timelordtty.dca.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 持仓控制器
 */
@RestController
@RequestMapping("/api/v2/holdings")
public class HoldingController {

    /**
     * 依赖的 HoldingService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final HoldingService holdingService;
    /**
     * 依赖的 UserService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final UserService userService;
    /**
     * 依赖的 FamilyService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final FamilyService familyService;

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    public HoldingController(HoldingService holdingService, UserService userService, FamilyService familyService) {
        this.holdingService = holdingService;
        this.userService = userService;
        this.familyService = familyService;
    }

    @GetMapping
    public ResponseEntity<List<HoldingService.HoldingInfo>> getHoldings(
            @RequestParam(required = false, defaultValue = "PERSONAL") String scope,
            @RequestParam(required = false) Long memberUserId) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        ScopeOwner owner = resolveScopeOwner(currentUser, scope, memberUserId);
        List<HoldingService.HoldingInfo> holdings = holdingService.calculateHoldings(owner.ownerUserId, owner.ownerFamilyId);
        return ResponseEntity.ok(holdings);
    }

    /**
     * 导入初始持仓
     */
    @PostMapping("/import-initial")
    public ResponseEntity<Void> importInitialHoldings(@RequestBody List<HoldingService.InitialHoldingImport> holdings) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        holdingService.importInitialHoldings(currentUser.getId(), currentUser.getFamilyId(), holdings);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取指定产品在各账户的持仓明细
     * 用于关联账户产品的赎回来源选择
     * 
     * @param productId 产品ID
     * @return 账户持仓明细列表
     */
    @GetMapping("/product/{productId}/by-account")
    public ResponseEntity<List<HoldingService.AccountHoldingInfo>> getProductHoldingsByAccount(
            @PathVariable Long productId,
            @RequestParam(required = false, defaultValue = "PERSONAL") String scope,
            @RequestParam(required = false) Long memberUserId) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        ScopeOwner owner = resolveScopeOwner(currentUser, scope, memberUserId);
        List<HoldingService.AccountHoldingInfo> holdings = holdingService.getProductHoldingsByAccount(
            productId, owner.ownerUserId, owner.ownerFamilyId);
        return ResponseEntity.ok(holdings);
    }

    private static class ScopeOwner {
        final Long ownerUserId;
        final Long ownerFamilyId;
        ScopeOwner(Long ownerUserId, Long ownerFamilyId) {
            this.ownerUserId = ownerUserId;
            this.ownerFamilyId = ownerFamilyId;
        }
    }

    private ScopeOwner resolveScopeOwner(AuthResponse.UserInfo currentUser, String scope, Long memberUserId) {
        String normalized = scope != null ? scope.trim().toUpperCase() : "PERSONAL";
        if ("PERSONAL".equals(normalized)) {
            // 普通用户默认只看个人数据（避免看到家庭共享数据）
            return new ScopeOwner(currentUser.getId(), null);
        }
        if ("FAMILY_ALL".equals(normalized)) {
            if (currentUser.getFamilyId() == null) {
                throw new RuntimeException("无家庭，无法查看家庭范围数据");
            }
            familyService.assertAdmin(currentUser.getId(), currentUser.getFamilyId());
            // 全家汇总：只按 familyId 过滤
            return new ScopeOwner(null, currentUser.getFamilyId());
        }
        if ("MEMBER".equals(normalized)) {
            if (currentUser.getFamilyId() == null) {
                throw new RuntimeException("无家庭，无法查看成员范围数据");
            }
            familyService.assertAdmin(currentUser.getId(), currentUser.getFamilyId());
            if (memberUserId == null) {
                throw new RuntimeException("memberUserId 不能为空");
            }
            // 成员下钻：仅查看该成员个人范围（不包含家庭共享）
            return new ScopeOwner(memberUserId, null);
        }
        throw new RuntimeException("不支持的 scope: " + scope);
    }
}

