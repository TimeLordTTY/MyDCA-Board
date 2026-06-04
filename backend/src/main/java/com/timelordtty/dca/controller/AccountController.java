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
     * 账户服务入口，负责账户树查询、账户归属校验、账户创建更新以及余额调整编排。
     */
    private final AccountService accountService;
    /**
     * 货币基金份额服务，用于在账户查询和同步场景中维护货币基金份额展示口径。
     */
    private final MmfSharesService mmfSharesService;
    /**
     * 用户身份服务，用于根据当前登录名定位用户、家庭和角色权限上下文。
     */
    private final UserService userService;
    /**
     * 家庭服务入口，用于校验家庭归属、管理员权限和成员范围。
     */
    private final FamilyService familyService;

    /**
     * 装配账户、货币基金份额、用户和家庭服务，支撑账户管理接口的身份与权限判断。
     */
    public AccountController(AccountService accountService, MmfSharesService mmfSharesService, UserService userService, FamilyService familyService) {
        this.accountService = accountService;
        this.mmfSharesService = mmfSharesService;
        this.userService = userService;
        this.familyService = familyService;
    }

    /**
     * 查询账户树：PERSONAL 返回当前用户账户，FAMILY_ALL 返回家庭账户并要求管理员权限，MEMBER 返回指定成员账户；该接口只读，不创建流水也不改余额。
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
     * 按账户 ID 查询单个账户详情，用于编辑页和余额展示前的只读加载。
     */
    @GetMapping("/{id}")
    public ResponseEntity<Account> getAccount(@PathVariable Long id) {
        Account account = accountService.getAccount(id);
        return ResponseEntity.ok(account);
    }

    /**
     * 创建账户并补齐 ownerUserId 或 ownerFamilyId；个人账户在存在 familyId 时也保留家庭归属，便于家庭视图展示；该操作新增账户记录但不直接生成账本分录。
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
     * 更新账户名称、类型、币种、所属范围等基础资料，不直接重算历史流水或持仓成本。
     */
    @PutMapping("/{id}")
    public ResponseEntity<Account> updateAccount(@PathVariable Long id, @RequestBody Account account) {
        account.setId(id);
        Account updated = accountService.updateAccount(account);
        return ResponseEntity.ok(updated);
    }

    /**
     * 执行账户余额调整入口，将调整金额和原因交由账户服务生成受控账本影响，避免直接在 Controller 修改余额。
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

