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
/**
 * 业务注释规范化: HoldingController 控制器，负责接收前端请求、读取用户上下文，并将业务处理委托给服务层。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class HoldingController {

    /**
     * 业务注释规范化: holdingService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final HoldingService holdingService;
    /**
     * 业务注释规范化: userService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final UserService userService;
    /**
     * 业务注释规范化: familyService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final FamilyService familyService;

    /**
     * 业务注释规范化: 处理 HoldingController 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param holdingService holdingService 业务字段，承载该对象在后端流程中的核心属性。
     * @param userService userService 业务字段，承载该对象在后端流程中的核心属性。
     * @param familyService familyService 业务字段，承载该对象在后端流程中的核心属性。
     */
    public HoldingController(HoldingService holdingService, UserService userService, FamilyService familyService) {
        this.holdingService = holdingService;
        this.userService = userService;
        this.familyService = familyService;
    }

    @GetMapping
    /**
     * 业务注释规范化: 查询 getHoldings 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param false false 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 处理 importInitialHoldings 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param holdings holdings 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 查询 getProductHoldingsByAccount 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @param false false 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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

    /**
     * 业务注释规范化: 处理 resolveScopeOwner 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该私有方法封装局部复杂逻辑，用于保持统计、展示或校验口径一致。</p>
     * @param currentUser currentUser 业务字段，承载该对象在后端流程中的核心属性。
     * @param scope scope 业务字段，承载该对象在后端流程中的核心属性。
     * @param memberUserId memberUserId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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

