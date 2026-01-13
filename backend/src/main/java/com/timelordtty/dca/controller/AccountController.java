package com.timelordtty.dca.controller;

import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.service.AccountService;
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

    private final AccountService accountService;
    private final UserService userService;

    public AccountController(AccountService accountService, UserService userService) {
        this.accountService = accountService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<Account>> getAccounts() {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        List<Account> accounts = accountService.getAccountTree(currentUser.getId(), currentUser.getFamilyId());
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Account> getAccount(@PathVariable Long id) {
        Account account = accountService.getAccount(id);
        return ResponseEntity.ok(account);
    }

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

    @PutMapping("/{id}")
    public ResponseEntity<Account> updateAccount(@PathVariable Long id, @RequestBody Account account) {
        account.setId(id);
        Account updated = accountService.updateAccount(account);
        return ResponseEntity.ok(updated);
    }

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
}

