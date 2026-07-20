package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.mobile.MobileAccountDto;
import com.timelordtty.dca.dto.mobile.MobileHoldingDto;
import com.timelordtty.dca.dto.mobile.MobilePageResponse;
import com.timelordtty.dca.dto.mobile.MobileTransactionDto;
import com.timelordtty.dca.dto.mobile.MobileWealthOverviewDto;
import com.timelordtty.dca.service.MobileWealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/mobile")
public class MobileWealthController {
    private final MobileWealthService mobileWealthService;

    public MobileWealthController(MobileWealthService mobileWealthService) {
        this.mobileWealthService = mobileWealthService;
    }

    @GetMapping("/overview")
    public ResponseEntity<MobileWealthOverviewDto> getOverview() {
        return ResponseEntity.ok(mobileWealthService.getOverview());
    }

    @GetMapping("/accounts")
    public ResponseEntity<MobilePageResponse<MobileAccountDto>> getAccounts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ResponseEntity.ok(mobileWealthService.getAccounts(page, pageSize));
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<MobileAccountDto> getAccountDetail(@PathVariable Long id) {
        return ResponseEntity.ok(mobileWealthService.getAccountDetail(id));
    }

    @GetMapping("/transactions")
    public ResponseEntity<MobilePageResponse<MobileTransactionDto>> getTransactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ResponseEntity.ok(mobileWealthService.getTransactions(page, pageSize));
    }

    @GetMapping("/holdings")
    public ResponseEntity<MobilePageResponse<MobileHoldingDto>> getHoldings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ResponseEntity.ok(mobileWealthService.getHoldings(page, pageSize));
    }
}
