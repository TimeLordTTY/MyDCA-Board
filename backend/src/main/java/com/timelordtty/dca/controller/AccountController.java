package com.timelordtty.dca.controller;

import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.service.AccountService;
import com.timelordtty.dca.service.FamilyService;
import com.timelordtty.dca.service.MmfSharesService;
import com.timelordtty.dca.service.UserService;
import com.timelordtty.dca.dto.AuthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 账户控制器
 */
@RestController
@RequestMapping("/api/v2/accounts")
/**
 * 业务注释规范化: AccountController 控制器，负责接收前端请求、读取用户上下文，并将业务处理委托给服务层。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class AccountController {

    /**
     * 业务注释规范化: accountService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final AccountService accountService;
    /**
     * 业务注释规范化: mmfSharesService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final MmfSharesService mmfSharesService;
    /**
     * 业务注释规范化: userService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final UserService userService;
    /**
     * 业务注释规范化: familyService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final FamilyService familyService;

    /**
     * 业务注释规范化: 处理 AccountController 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountService accountService 业务字段，承载该对象在后端流程中的核心属性。
     * @param mmfSharesService mmfSharesService 业务字段，承载该对象在后端流程中的核心属性。
     * @param userService userService 业务字段，承载该对象在后端流程中的核心属性。
     * @param familyService familyService 业务字段，承载该对象在后端流程中的核心属性。
     */
    public AccountController(AccountService accountService, MmfSharesService mmfSharesService, UserService userService, FamilyService familyService) {
        this.accountService = accountService;
        this.mmfSharesService = mmfSharesService;
        this.userService = userService;
        this.familyService = familyService;
    }

    @GetMapping
    /**
     * 业务注释规范化: 查询 getAccounts 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param false false 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<List<Account>> getAccounts(
            @RequestParam(required = false, defaultValue = "PERSONAL") String scope,
            @RequestParam(required = false) Long memberUserId) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        ScopeOwner owner = resolveScopeOwner(currentUser, scope, memberUserId);
        List<Account> accounts = accountService.getAccountTree(owner.ownerUserId, owner.ownerFamilyId);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}")
    /**
     * 业务注释规范化: 查询 getAccount 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<Account> getAccount(@PathVariable Long id) {
        Account account = accountService.getAccount(id);
        return ResponseEntity.ok(account);
    }

    @PostMapping
    /**
     * 业务注释规范化: 创建 createAccount 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param account account 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<Account> createAccount(@RequestBody Account account) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        // 设置归属
        if (account.getOwnerType() == null) {
            account.setOwnerType("PERSONAL");
        }
        if ("PERSONAL".equals(account.getOwnerType())) {
            account.setOwnerUserId(currentUser.getId());
            // 即使是个账户，如果用户有家庭ID，也设置 familyId，这样账户可以在家庭视图中显示
            if (currentUser.getFamilyId() != null) {
                account.setOwnerFamilyId(currentUser.getFamilyId());
            }
        } else if ("FAMILY".equals(account.getOwnerType())) {
            account.setOwnerFamilyId(currentUser.getFamilyId());
            // 家庭账户也需要设置 ownerUserId（创建者）
            account.setOwnerUserId(currentUser.getId());
        }

        Account created = accountService.createAccount(account);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    /**
     * 业务注释规范化: 更新 updateAccount 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @param account account 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<Account> updateAccount(@PathVariable Long id, @RequestBody Account account) {
        account.setId(id);
        Account updated = accountService.updateAccount(account);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/balance")
    /**
     * 业务注释规范化: 处理 adjustBalance 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @param request request 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<Void> adjustBalance(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        BigDecimal newBalance = new BigDecimal(request.get("balance").toString());
        accountService.adjustBalance(id, newBalance);
        return ResponseEntity.ok().build();
    }

    /**
     * 重新计算指定账户的余额（从分录中计算）
     * 
     * 适用于：修复历史数据、数据迁移、余额不一致等情况
     * 
     * @param id 账户ID
     * @return 重新计算后的余额
     */
    @PostMapping("/{id}/recalculate-balance")
    /**
     * 业务注释规范化: 重新计算 recalculateBalance 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<Map<String, Object>> recalculateBalance(@PathVariable Long id) {
        BigDecimal newBalance = accountService.recalculateBalance(id);
        return ResponseEntity.ok(Map.of("accountId", id, "balance", newBalance));
    }

    /**
     * 重新计算所有账户的余额（从分录中计算）
     * 
     * 适用于：修复历史数据、数据迁移、余额不一致等情况
     * 
     * @return 重新计算的账户数量
     */
    @PostMapping("/recalculate-all-balances")
    /**
     * 业务注释规范化: 重新计算 recalculateAllBalances 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<Map<String, Object>> recalculateAllBalances() {
        int count = accountService.recalculateAllBalances();
        return ResponseEntity.ok(Map.of("recalculatedCount", count));
    }

    /**
     * 获取 MMF 平台的份额分配详情
     * 
     * @param id MMF 平台账户 ID
     * @return 份额分配详情
     */
    @GetMapping("/{id}/mmf-shares")
    /**
     * 业务注释规范化: 查询 getMmfSharesDetail 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<MmfSharesService.MmfSharesDetail> getMmfSharesDetail(@PathVariable Long id) {
        MmfSharesService.MmfSharesDetail detail = mmfSharesService.calculateShares(id);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
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
            return new ScopeOwner(currentUser.getId(), null);
        }
        if ("FAMILY_ALL".equals(normalized)) {
            if (currentUser.getFamilyId() == null) {
                throw new RuntimeException("无家庭，无法查看家庭范围数据");
            }
            familyService.assertAdmin(currentUser.getId(), currentUser.getFamilyId());
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
            return new ScopeOwner(memberUserId, null);
        }
        throw new RuntimeException("不支持的 scope: " + scope);
    }
}

