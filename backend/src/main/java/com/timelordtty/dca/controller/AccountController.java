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
public class AccountController {

    /**
     * 依赖的 AccountService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final AccountService accountService;
    /**
     * 依赖的 MmfSharesService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final MmfSharesService mmfSharesService;
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
    public AccountController(AccountService accountService, MmfSharesService mmfSharesService, UserService userService, FamilyService familyService) {
        this.accountService = accountService;
        this.mmfSharesService = mmfSharesService;
        this.userService = userService;
        this.familyService = familyService;
    }

    /**
     * 处理只读查询 API，按当前用户和请求参数返回对应资源，不写入账本或修改持仓。
     */
    @GetMapping
    public ResponseEntity<List<Account>> getAccounts(
            @RequestParam(required = false, defaultValue = "PERSONAL") String scope,
            @RequestParam(required = false) Long memberUserId) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        ScopeOwner owner = resolveScopeOwner(currentUser, scope, memberUserId);
        List<Account> accounts = accountService.getAccountTree(owner.ownerUserId, owner.ownerFamilyId);
        return ResponseEntity.ok(accounts);
    }

    /**
     * 返回当前场景的业务数据，用于前后端传递或服务层计算。
     */
    @GetMapping("/{id}")
    public ResponseEntity<Account> getAccount(@PathVariable Long id) {
        Account account = accountService.getAccount(id);
        return ResponseEntity.ok(account);
    }

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    @PostMapping
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

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    @PutMapping("/{id}")
    public ResponseEntity<Account> updateAccount(@PathVariable Long id, @RequestBody Account account) {
        account.setId(id);
        Account updated = accountService.updateAccount(account);
        return ResponseEntity.ok(updated);
    }

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    @PutMapping("/{id}/balance")
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

