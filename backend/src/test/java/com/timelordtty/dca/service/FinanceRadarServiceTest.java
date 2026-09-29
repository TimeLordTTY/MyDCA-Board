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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinanceRadarServiceTest {
    private final AccountMapper accounts = mock(AccountMapper.class);
    private final HoldingService holdings = mock(HoldingService.class);
    private final DraftLedgerEntryMapper drafts = mock(DraftLedgerEntryMapper.class);
    private final OrderMapper orders = mock(OrderMapper.class);
    private final SettlementAuditService audits = mock(SettlementAuditService.class);
    private final MarketService market = mock(MarketService.class);
    private final NavMapper navs = mock(NavMapper.class);
    private final ProductMasterMapper products = mock(ProductMasterMapper.class);
    private final IndicatorService indicators = mock(IndicatorService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-29T04:00:00Z"), ZoneOffset.UTC);
    private final FinanceRadarService radar = new FinanceRadarService(
            accounts, holdings, drafts, orders, audits, market, navs, products, indicators, clock);

    @Test void emptyPersonalDataIsZeroOnlyWhereKnown() {
        FinanceRadarDTO result = radar.getRadar(1L, 7L, "PERSONAL");
        assertEquals(BigDecimal.ZERO, result.assets().totalAssets());
        assertEquals(0, result.counts().drafts());
        assertEquals(0, result.counts().pendingOrders());
        assertNull(result.counts().outbox());
        assertTrue(result.warnings().stream().anyMatch(w -> w.code().equals("OUTBOX_UNKNOWN")));
        verify(accounts).selectByOwner(1L, null);
        verify(holdings).calculateHoldings(1L, null);
        verify(drafts).countVisibleByStatus(1L, null, "DRAFT");
        verify(orders).selectByUserId(1L);
        verifyNoBusinessWrites();
    }

    @Test void familyReadsOnlyFamilyRowsAndDoesNotClaimOrderCoverage() {
        when(drafts.countVisibleByStatus(null, 7L, "DRAFT")).thenReturn(3);
        FinanceRadarDTO result = radar.getRadar(1L, 7L, "FAMILY");
        assertEquals(3, result.counts().drafts());
        assertNull(result.counts().pendingOrders());
        assertNull(result.counts().reconciliationBroken());
        verify(accounts).selectByOwner(null, 7L);
        verify(holdings).calculateHoldings(null, 7L);
        verify(drafts).countVisibleByStatus(null, 7L, "DRAFT");
        verifyNoInteractions(orders, audits);
        verifyNoBusinessWrites();
    }

    @Test void pendingAndBrokenFactsAreCountedWithoutWriting() {
        when(drafts.countVisibleByStatus(1L, null, "DRAFT")).thenReturn(2);
        Order pending = new Order(); pending.setStatus("PENDING");
        Order confirmed = new Order(); confirmed.setStatus("CONFIRMED");
        when(orders.selectByUserId(1L)).thenReturn(List.of(pending, confirmed));
        SettlementAuditDTO broken = new SettlementAuditDTO(); broken.setReconciliationStatus("BROKEN");
        SettlementAuditDTO warning = new SettlementAuditDTO(); warning.setReconciliationStatus("WARNING");
        when(audits.history()).thenReturn(List.of(broken, warning));
        FinanceRadarDTO result = radar.getRadar(1L, null, "PERSONAL");
        assertEquals(2, result.counts().drafts());
        assertEquals(1, result.counts().pendingOrders());
        assertEquals(1, result.counts().awaitingSettlement());
        assertEquals(1, result.counts().reconciliationWarning());
        assertEquals(1, result.counts().reconciliationBroken());
        verifyNoBusinessWrites();
    }

    @Test void missingAndOldMarketDataNeverBecomeZeroValueOrHealthy() {
        HoldingService.HoldingInfo first = holding(10L, "2", "20");
        HoldingService.HoldingInfo second = holding(11L, "3", "30");
        when(holdings.calculateHoldings(1L, null)).thenReturn(List.of(first, second));
        when(products.selectById(10L)).thenReturn(product("CNY"));
        when(products.selectById(11L)).thenReturn(product("CNY"));
        MarketBarDaily oldBar = new MarketBarDaily(); oldBar.setTradeDate(LocalDate.of(2026, 9, 20));
        Nav oldNav = new Nav(); oldNav.setNavDate(LocalDate.of(2026, 9, 20));
        when(market.getLatestBar(11L)).thenReturn(oldBar);
        when(navs.selectLatest(11L)).thenReturn(oldNav);
        IndicatorDaily oldIndicator = new IndicatorDaily();
        oldIndicator.setTradeDate(LocalDate.of(2026, 9, 20));
        when(indicators.getLatestIndicator(11L, 20)).thenReturn(oldIndicator);
        FinanceRadarDTO result = radar.getRadar(1L, null, "PERSONAL");
        assertNull(result.assets().positionValue());
        assertNull(result.assets().totalAssets());
        assertEquals("UNKNOWN", result.markets().get(0).status());
        assertEquals("WARNING", result.markets().get(1).status());
        assertEquals("WARNING", result.markets().get(1).indicatorStatus());
        verifyNoBusinessWrites();
    }

    @Test void freshValuationSumsLeafCashWithoutCountingLinkedAccountsTwice() {
        Account parent = account(1L, null, "CNY", "1000");
        Account leaf = account(2L, 1L, "CNY", "100");
        Account linked = account(3L, null, "CNY", "50"); linked.setLinkedProductId(10L);
        linked.setInitialShares(new BigDecimal("2"));
        when(accounts.selectByOwner(1L, null)).thenReturn(List.of(parent, leaf, linked));
        HoldingService.HoldingInfo holding = holding(10L, "2", "20");
        holding.setMarketValue(new BigDecimal("24"));
        when(holdings.calculateHoldings(1L, null)).thenReturn(List.of(holding));
        when(products.selectById(10L)).thenReturn(product("CNY"));
        MarketBarDaily bar = new MarketBarDaily(); bar.setTradeDate(LocalDate.of(2026, 9, 29));
        Nav nav = new Nav(); nav.setNavDate(LocalDate.of(2026, 9, 29));
        IndicatorDaily indicator = new IndicatorDaily(); indicator.setTradeDate(LocalDate.of(2026, 9, 29));
        when(market.getLatestBar(10L)).thenReturn(bar);
        when(navs.selectLatest(10L)).thenReturn(nav);
        when(indicators.getLatestIndicator(10L, 20)).thenReturn(indicator);
        FinanceRadarDTO result = radar.getRadar(1L, null, "PERSONAL");
        assertEquals(new BigDecimal("100"), result.assets().cashBalance());
        assertEquals(new BigDecimal("124"), result.assets().totalAssets());
        assertEquals("OK", result.markets().get(0).status());
        verifyNoBusinessWrites();
    }

    @Test void foreignCurrencyHoldingMakesCombinedAssetsUnknown() {
        when(holdings.calculateHoldings(1L, null)).thenReturn(List.of(holding(10L, "2", "20")));
        when(products.selectById(10L)).thenReturn(product("USD"));
        FinanceRadarDTO result = radar.getRadar(1L, null, "PERSONAL");
        assertNull(result.assets().investmentCost());
        assertNull(result.assets().totalAssets());
        assertTrue(result.warnings().stream().anyMatch(w -> w.code().equals("HOLDING_CURRENCY_UNKNOWN")));
        verifyNoBusinessWrites();
    }

    private static ProductMaster product(String currency) {
        ProductMaster product = new ProductMaster(); product.setCurrency(currency);
        return product;
    }

    private static Account account(Long id, Long parentId, String currency, String balance) {
        Account account = new Account();
        account.setId(id); account.setParentAccountId(parentId); account.setAccountKind("REAL");
        account.setAccountType("CASH"); account.setCurrency(currency);
        account.setBalance(new BigDecimal(balance));
        return account;
    }

    private static HoldingService.HoldingInfo holding(Long productId, String shares, String cost) {
        HoldingService.HoldingInfo holding = new HoldingService.HoldingInfo();
        holding.setProductId(productId);
        holding.setTotalShares(new BigDecimal(shares));
        holding.setTotalCost(new BigDecimal(cost));
        return holding;
    }

    private void verifyNoBusinessWrites() {
        verify(accounts, never()).insert(any());
        verify(accounts, never()).update(any());
        verify(drafts, never()).insert(any());
        verify(drafts, never()).markConfirmed(anyLong(), any(), any());
        verify(orders, never()).insert(any());
        verify(orders, never()).update(any());
        verify(navs, never()).insert(any());
    }
}
