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
        } else if ("FAMILY".equals(account.getOwnerType())) {
            account.setOwnerFamilyId(currentUser.getFamilyId());
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
}

