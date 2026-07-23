package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.LedgerStatsQueryDTO;
import com.timelordtty.dca.dto.LedgerStatsSummaryDTO;
import com.timelordtty.dca.dto.TodayTodoDTO;
import com.timelordtty.dca.dto.mobile.MobileAccountDto;
import com.timelordtty.dca.dto.mobile.MobileCashFlowDto;
import com.timelordtty.dca.dto.mobile.MobileActivityItemDto;
import com.timelordtty.dca.dto.mobile.MobileHoldingDto;
import com.timelordtty.dca.dto.mobile.MobilePageResponse;
import com.timelordtty.dca.dto.mobile.MobileTransactionDto;
import com.timelordtty.dca.dto.mobile.MobileWealthOverviewDto;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.service.DashboardService.AssetOverview;
import com.timelordtty.dca.service.HoldingService.HoldingInfo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MobileWealthService {
    private final UserService userService;
    private final DashboardService dashboardService;
    private final AccountService accountService;
    private final HoldingService holdingService;
    private final LedgerService ledgerService;
    private final TodoService todoService;

    public MobileWealthService(
            UserService userService,
            DashboardService dashboardService,
            AccountService accountService,
            HoldingService holdingService,
            LedgerService ledgerService,
            TodoService todoService
    ) {
        this.userService = userService;
        this.dashboardService = dashboardService;
        this.accountService = accountService;
        this.holdingService = holdingService;
        this.ledgerService = ledgerService;
        this.todoService = todoService;
    }

    public MobileWealthOverviewDto getOverview() {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        AssetOverview overview = dashboardService.getAssetOverview(currentUser.getId(), currentUser.getFamilyId(), "PERSONAL");
        TodayTodoDTO todos = todoService.getTodayTodos(currentUser.getId(), currentUser.getFamilyId());
        List<Account> accounts = flattenAccounts(accountService.getAccountTree(currentUser.getId(), null));
        MobilePageResponse<MobileTransactionDto> transactions = getTransactions(1, 5);

        MobileWealthOverviewDto dto = new MobileWealthOverviewDto();
        dto.setTotalAssets(overview.getTotalAssets());
        dto.setCashBalance(overview.getCashBalance());
        dto.setPositionValue(overview.getPositionValue());
        dto.setTotalLiabilities(overview.getTotalLiabilities());
        dto.setNetWorth(overview.getNetWorth());
        dto.setAccountCount(accounts.size());
        dto.setSpendableAmount(sumFundUsage(accounts, "SPENDABLE"));
        dto.setReservedFundAmount(sumFundUsage(accounts, "RESERVED"));
        dto.setInvestableAmount(sumFundUsage(accounts, "INVESTABLE"));
        dto.setUnallocatedAmount(sumUnallocated(accounts));
        dto.setDraftCount(todos.getDraftCount());
        dto.setSettlementCount(todos.getSettlementCount());
        dto.setSuggestionCount(todos.getSuggestionCount());
        dto.setTotalTodoCount(todos.getTotalCount());
        dto.setRecentActivities(toActivities(transactions.getItems()));
        dto.setLastUpdatedAt(
                transactions.getItems().stream()
                        .map(MobileTransactionDto::getRequestedAt)
                        .filter(java.util.Objects::nonNull)
                        .max(LocalDateTime::compareTo)
                        .orElse(null)
        );
        return dto;
    }

    public MobilePageResponse<MobileAccountDto> getAccounts(int page, int pageSize) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        List<MobileAccountDto> items = flattenAllAccounts(accountService.getAccountTree(currentUser.getId(), null)).stream()
                .map(this::toAccountDto)
                .sorted(Comparator.comparing(MobileAccountDto::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return page(items, page, pageSize);
    }

    public MobileAccountDto getAccountDetail(Long id) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        return flattenAllAccounts(accountService.getAccountTree(currentUser.getId(), null)).stream()
                .filter(account -> id.equals(account.getId()))
                .findFirst()
                .map(this::toAccountDto)
                .orElseThrow(() -> new RuntimeException("账户不存在或无权访问"));
    }

    public MobilePageResponse<MobileHoldingDto> getHoldings(int page, int pageSize) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        List<MobileHoldingDto> items = holdingService.calculateHoldings(currentUser.getId(), null).stream()
                .map(this::toHoldingDto)
                .sorted(Comparator.comparing(MobileHoldingDto::getMarketValue, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return page(items, page, pageSize);
    }

    public MobileCashFlowDto getCurrentMonthCashFlow() {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LedgerStatsQueryDTO query = new LedgerStatsQueryDTO();
        query.setStartDate(start);
        query.setEndDate(LocalDate.now());
        query.setIncludeTransfer(false);
        LedgerStatsSummaryDTO summary = ledgerService.getLedgerStatsSummary(currentUser, query);
        MobileCashFlowDto dto = new MobileCashFlowDto();
        dto.setMonthStart(start);
        dto.setMonthEnd(LocalDate.now());
        dto.setIncome(summary.getTotalIncome());
        dto.setExpense(summary.getTotalExpense());
        dto.setNetCashFlow(summary.getNetCashflow());
        dto.setInvestmentInflow(summary.getInvestmentInflow());
        dto.setInvestmentOutflow(summary.getInvestmentOutflow());
        dto.setRecentActivities(toActivities(getTransactions(1, 5).getItems()));
        return dto;
    }

    public MobilePageResponse<MobileTransactionDto> getTransactions(int page, int pageSize) {
        AuthResponse.UserInfo currentUser = userService.getCurrentUser();
        List<com.timelordtty.dca.model.LedgerTxn> txns = ledgerService.getTransactions(
                currentUser.getId(), null, null, null, null, null, null, null, page, pageSize
        );
        int total = ledgerService.countTransactions(currentUser.getId(), null, null, null, null, null, null, null);
        List<MobileTransactionDto> items = new ArrayList<>();
        for (com.timelordtty.dca.model.LedgerTxn txn : txns) {
            MobileTransactionDto dto = new MobileTransactionDto();
            dto.setTxnId(txn.getTxnId());
            dto.setTxnType(txn.getTxnType());
            dto.setStatus(txn.getStatus());
            dto.setNote(txn.getNote());
            dto.setRequestedAt(txn.getRequestedAt());
            dto.setTradeDate(txn.getTradeDate());
            dto.setCurrency("CNY");
            dto.setAmount(resolveTxnAmount(txn.getTxnId()));
            dto.setAccountName(resolveTxnAccountName(txn.getTxnId()));
            items.add(dto);
        }
        MobilePageResponse<MobileTransactionDto> response = new MobilePageResponse<>();
        response.setItems(items);
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(total);
        response.setTotalPages((int) Math.ceil(total / (double) pageSize));
        response.setHasNext(page * pageSize < total);
        return response;
    }

    private BigDecimal resolveTxnAmount(String txnId) {
        List<com.timelordtty.dca.model.LedgerPosting> postings = ledgerService.getPostingsByTxnIds(List.of(txnId));
        return postings.stream()
                .filter(posting -> posting.getAmount() != null)
                .map(com.timelordtty.dca.model.LedgerPosting::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private String resolveTxnAccountName(String txnId) {
        List<com.timelordtty.dca.model.LedgerPosting> postings = ledgerService.getPostingsByTxnIds(List.of(txnId));
        return postings.stream()
                .map(com.timelordtty.dca.model.LedgerPosting::getAccountId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .map(accountService::getAccount)
                .map(Account::getAccountName)
                .orElse(null);
    }

    private List<MobileActivityItemDto> toActivities(List<MobileTransactionDto> items) {
        return items.stream().map(item -> {
            MobileActivityItemDto dto = new MobileActivityItemDto();
            dto.setTxnId(item.getTxnId());
            dto.setTxnType(item.getTxnType());
            dto.setStatus(item.getStatus());
            dto.setNote(item.getNote());
            dto.setAccountName(item.getAccountName());
            dto.setAmount(item.getAmount());
            dto.setCurrency(item.getCurrency());
            dto.setOccurredAt(item.getRequestedAt());
            return dto;
        }).toList();
    }

    private MobileAccountDto toAccountDto(Account account) {
        MobileAccountDto dto = new MobileAccountDto();
        dto.setId(account.getId());
        dto.setParentAccountId(account.getParentAccountId());
        dto.setAccountName(account.getAccountName());
        dto.setAccountType(account.getAccountType());
        dto.setFundUsage(account.getFundUsage());
        boolean leaf = account.getChildren() == null || account.getChildren().isEmpty();
        dto.setLeaf(leaf);
        boolean spendableLeaf = leaf && "REAL".equals(account.getAccountKind()) && "SPENDABLE".equals(account.getFundUsage());
        dto.setSelectableForExpense(spendableLeaf);
        dto.setSafetyMessage(accountSafetyMessage(account, leaf));
        dto.setCurrency(account.getCurrency());
        dto.setBalance(aggregateBalance(account));
        dto.setReservedAmount(account.getReservedAmount());
        dto.setAvailableAmount((account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO)
                .subtract(account.getReservedAmount() != null ? account.getReservedAmount() : BigDecimal.ZERO));
        dto.setActive(account.getIsActive());
        dto.setUpdatedAt(account.getUpdatedAt());
        if (account.getParentAccountId() != null) {
            Account parent = accountService.getAccount(account.getParentAccountId());
            dto.setParentAccountName(parent != null ? parent.getAccountName() : null);
        }
        return dto;
    }

    private MobileHoldingDto toHoldingDto(HoldingInfo holding) {
        MobileHoldingDto dto = new MobileHoldingDto();
        dto.setProductId(holding.getProductId());
        dto.setProductCode(holding.getProductCode());
        dto.setProductName(holding.getProductName());
        dto.setAccountName(holding.getBrokerAccountName());
        dto.setTotalShares(holding.getTotalShares());
        dto.setTotalCost(holding.getTotalCost());
        dto.setAvgCost(holding.getAvgCost());
        dto.setMarketValue(holding.getMarketValue());
        dto.setUnrealizedPnl(holding.getUnrealizedPnl());
        return dto;
    }

    private List<Account> flattenAccounts(List<Account> roots) {
        List<Account> result = new ArrayList<>();
        for (Account account : roots) {
            if (account.getChildren() == null || account.getChildren().isEmpty()) {
                result.add(account);
            } else {
                result.addAll(account.getChildren());
            }
        }
        return result;
    }

    private List<Account> flattenAllAccounts(List<Account> roots) {
        List<Account> result = new ArrayList<>();
        for (Account account : roots) {
            result.add(account);
            if (account.getChildren() != null) result.addAll(flattenAllAccounts(account.getChildren()));
        }
        return result;
    }

    private BigDecimal aggregateBalance(Account account) {
        if (account.getChildren() == null || account.getChildren().isEmpty()) {
            return account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        }
        return account.getChildren().stream().map(this::aggregateBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal sumFundUsage(List<Account> accounts, String usage) {
        return accounts.stream().filter(MobileWealthService::isCashLeaf)
                .filter(account -> usage.equals(account.getFundUsage()))
                .map(Account::getBalance).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal sumUnallocated(List<Account> accounts) {
        return accounts.stream().filter(MobileWealthService::isCashLeaf)
                .filter(account -> account.getFundUsage() == null || account.getFundUsage().isBlank())
                .map(Account::getBalance).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isCashLeaf(Account account) {
        return "REAL".equals(account.getAccountKind()) && "CASH".equals(account.getAccountType())
                && (account.getChildren() == null || account.getChildren().isEmpty());
    }

    private String accountSafetyMessage(Account account, boolean leaf) {
        if (!leaf) return "父账户仅用于聚合展示，不能作为记账来源";
        if (account.getFundUsage() == null || account.getFundUsage().isBlank()) return "待分配账户不应默认用于消费或投资";
        if ("RESERVED".equals(account.getFundUsage())) return "专款账户不得用于日常消费";
        if ("INVESTABLE".equals(account.getFundUsage())) return "可投资资金不得用于日常消费";
        return "可用于日常消费，正式入账仍需预览和二次确认";
    }

    private <T> MobilePageResponse<T> page(List<T> items, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePage - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        List<T> slice = items.subList(fromIndex, toIndex);
        MobilePageResponse<T> response = new MobilePageResponse<>();
        response.setItems(slice);
        response.setPage(safePage);
        response.setPageSize(safePageSize);
        response.setTotal(items.size());
        response.setTotalPages((int) Math.ceil(items.size() / (double) safePageSize));
        response.setHasNext(toIndex < items.size());
        return response;
    }
}
