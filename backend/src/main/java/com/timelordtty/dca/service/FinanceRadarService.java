package com.timelordtty.dca.service;

import com.timelordtty.dca.dto.FinanceRadarDTO;
import com.timelordtty.dca.dto.SettlementAuditDTO;
import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.DraftLedgerEntryMapper;
import com.timelordtty.dca.mapper.NavMapper;
import com.timelordtty.dca.mapper.OrderMapper;
import com.timelordtty.dca.mapper.ProductMasterMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.IndicatorDaily;
import com.timelordtty.dca.model.MarketBarDaily;
import com.timelordtty.dca.model.Nav;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.model.ProductMaster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Aggregates existing read paths only; no draft, order, settlement or ledger mutation. */
@Service
public class FinanceRadarService {
    private final AccountMapper accounts;
    private final HoldingService holdings;
    private final DraftLedgerEntryMapper drafts;
    private final OrderMapper orders;
    private final SettlementAuditService audits;
    private final MarketService market;
    private final NavMapper navs;
    private final ProductMasterMapper products;
    private final IndicatorService indicators;
    private final Clock clock;

    @Autowired
    public FinanceRadarService(AccountMapper accounts, HoldingService holdings, DraftLedgerEntryMapper drafts,
                               OrderMapper orders, SettlementAuditService audits, MarketService market,
                               NavMapper navs, ProductMasterMapper products, IndicatorService indicators) {
        this(accounts, holdings, drafts, orders, audits, market, navs, products, indicators,
                Clock.systemDefaultZone());
    }

    FinanceRadarService(AccountMapper accounts, HoldingService holdings, DraftLedgerEntryMapper drafts,
                        OrderMapper orders, SettlementAuditService audits, MarketService market,
                        NavMapper navs, ProductMasterMapper products, IndicatorService indicators, Clock clock) {
        this.accounts = accounts;
        this.holdings = holdings;
        this.drafts = drafts;
        this.orders = orders;
        this.audits = audits;
        this.market = market;
        this.navs = navs;
        this.products = products;
        this.indicators = indicators;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinanceRadarDTO getRadar(Long userId, Long familyId, String scope) {
        boolean family = "FAMILY".equals(scope);
        Long scopedUser = family ? null : userId;
        Long scopedFamily = family ? familyId : null;
        LocalDate today = LocalDate.now(clock);
        List<FinanceRadarDTO.Warning> warnings = new ArrayList<>();
        List<Account> visibleAccounts = accounts.selectByOwner(scopedUser, scopedFamily);
        if (visibleAccounts == null) visibleAccounts = List.of();
        Set<Long> parents = new HashSet<>();
        for (Account account : visibleAccounts) {
            if (account.getParentAccountId() != null) parents.add(account.getParentAccountId());
        }
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;
        boolean cashKnown = true;
        for (Account account : visibleAccounts) {
            if (!"REAL".equals(account.getAccountKind()) || parents.contains(account.getId())) continue;
            if (account.getLinkedProductId() != null) {
                if (account.getInitialShares() == null || account.getInitialShares().signum() <= 0) {
                    cashKnown = false;
                    warnings.add(warning("LINKED_SHARES_UNKNOWN", "UNKNOWN",
                            "关联产品账户缺少有效份额，资产汇总不完整"));
                }
                continue; // linked shares are counted as holdings
            }
            boolean liability = account.getAccountType() != null
                    && Set.of("CREDIT_CARD", "HUABEI", "BAITIAO", "LOAN").contains(account.getAccountType());
            if (!"CNY".equals(account.getCurrency()) || account.getBalance() == null) {
                cashKnown = false;
                warnings.add(warning("ACCOUNT_VALUE_UNKNOWN", "UNKNOWN", "账户币种或余额无法纳入人民币汇总"));
                continue;
            }
            if (liability) liabilities = liabilities.add(account.getBalance());
            else cash = cash.add(account.getBalance());
        }

        List<HoldingService.HoldingInfo> visibleHoldings = holdings.calculateHoldings(scopedUser, scopedFamily);
        if (visibleHoldings == null) visibleHoldings = List.of();
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        boolean costKnown = true;
        boolean valueKnown = true;
        Set<Long> seenProducts = new HashSet<>();
        List<FinanceRadarDTO.MarketFact> markets = new ArrayList<>();
        for (HoldingService.HoldingInfo holding : visibleHoldings) {
            if (holding.getTotalShares() == null || holding.getTotalShares().signum() <= 0) continue;
            if (holding.getTotalCost() == null) {
                costKnown = false;
                warnings.add(warning("HOLDING_COST_UNKNOWN", "UNKNOWN", "有持仓缺少成本数据"));
            } else cost = cost.add(holding.getTotalCost());
            Long productId = holding.getProductId();
            if (productId == null) {
                valueKnown = false;
                costKnown = false;
                warnings.add(warning("HOLDING_PRODUCT_UNKNOWN", "UNKNOWN", "有持仓缺少产品标识"));
                continue;
            }
            ProductMaster product = products.selectById(productId);
            if (product == null || !"CNY".equals(product.getCurrency())) {
                valueKnown = false;
                costKnown = false;
                warnings.add(warning("HOLDING_CURRENCY_UNKNOWN", "UNKNOWN",
                        "产品 " + productId + " 币种无法纳入人民币汇总"));
            }
            // HoldingService values positions using NAV; a fresh quote cannot validate an old NAV valuation.
            MarketBarDaily bar = market.getLatestBar(productId);
            Nav nav = navs.selectLatest(productId);
            LocalDate priceDate = bar == null ? null : bar.getTradeDate();
            String priceStatus = freshness(priceDate, today, 3);
            String valuationStatus = freshness(nav == null ? null : nav.getNavDate(), today, 3);
            if (!"OK".equals(valuationStatus)) priceStatus = valuationStatus;
            if (!"OK".equals(priceStatus) || holding.getMarketValue() == null) {
                valueKnown = false;
                warnings.add(warning("MARKET_" + priceStatus, priceStatus,
                        "产品 " + productId + " 行情缺失或过旧，持仓市值不可作为今日估值"));
            } else value = value.add(holding.getMarketValue());
            if (seenProducts.add(productId)) {
                IndicatorDaily indicator = indicators.getLatestIndicator(productId, 20);
                LocalDate indicatorDate = indicator == null ? null : indicator.getTradeDate();
                String indicatorStatus = freshness(indicatorDate, today, 3);
                if (!"OK".equals(indicatorStatus))
                    warnings.add(warning("INDICATOR_" + indicatorStatus, indicatorStatus,
                            "产品 " + productId + " 指标缺失或过旧"));
                markets.add(new FinanceRadarDTO.MarketFact(productId, priceStatus, priceDate,
                        nav == null ? null : nav.getNavDate(),
                        indicatorStatus, indicatorDate));
            }
        }
        int draftCount = drafts.countVisibleByStatus(scopedUser, scopedFamily, "DRAFT");
        Integer pending = null;
        Integer awaiting = null;
        Integer reconciliationWarning = null;
        Integer reconciliationBroken = null;
        if (family) {
            warnings.add(warning("FAMILY_ORDER_SCOPE_UNKNOWN", "UNKNOWN",
                    "订单没有家庭归属字段，家庭订单与结算对账数量无法完整统计"));
        } else {
            List<Order> ownOrders = orders.selectByUserId(userId);
            if (ownOrders == null) ownOrders = List.of();
            pending = (int) ownOrders.stream().filter(o -> "PENDING".equals(o.getStatus())).count();
            awaiting = pending; // current settlement workflow accepts all PENDING investment orders
            List<SettlementAuditDTO> history = audits.history();
            if (history == null) history = List.of();
            reconciliationWarning = (int) history.stream().filter(a -> "WARNING".equals(a.getReconciliationStatus())).count();
            reconciliationBroken = (int) history.stream().filter(a -> "BROKEN".equals(a.getReconciliationStatus())).count();
        }
        warnings.add(warning("OUTBOX_UNKNOWN", "UNKNOWN", "Outbox 保存在设备本地，服务端无法统计数量"));
        BigDecimal reportedCash = cashKnown ? cash : null;
        BigDecimal reportedLiabilities = cashKnown ? liabilities : null;
        BigDecimal reportedValue = valueKnown ? value : null;
        BigDecimal total = cashKnown && valueKnown ? cash.add(value) : null;
        FinanceRadarDTO.Assets assets = new FinanceRadarDTO.Assets(
                total == null ? "UNKNOWN" : "OK", reportedCash, costKnown ? cost : null, reportedValue,
                reportedLiabilities, total, total == null ? null : total.subtract(liabilities));
        return new FinanceRadarDTO(today, scope, assets,
                new FinanceRadarDTO.Counts(draftCount, null, pending, awaiting,
                        reconciliationWarning, reconciliationBroken), markets, warnings);
    }

    private static String freshness(LocalDate date, LocalDate today, int maxAgeDays) {
        if (date == null) return "UNKNOWN";
        return date.isAfter(today) || date.isBefore(today.minusDays(maxAgeDays)) ? "WARNING" : "OK";
    }

    private static FinanceRadarDTO.Warning warning(String code, String status, String message) {
        return new FinanceRadarDTO.Warning(code, status, message);
    }
}
