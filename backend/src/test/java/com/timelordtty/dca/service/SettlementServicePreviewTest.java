package com.timelordtty.dca.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.timelordtty.dca.dto.AuthResponse;
import com.timelordtty.dca.dto.SettlementPostingPreviewDTO;
import com.timelordtty.dca.dto.SettlementPreviewDTO;
import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.OrderFundingLineMapper;
import com.timelordtty.dca.mapper.OrderMapper;
import com.timelordtty.dca.mapper.ProductMasterMapper;
import com.timelordtty.dca.mapper.SettlementConfirmMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.model.OrderFundingLine;
import com.timelordtty.dca.model.ProductMaster;
import com.timelordtty.dca.model.SettlementConfirm;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * v0.13.0 人工结算预览与二次确认：仅验证预览只读规则、fresh preview 令牌门禁与 confirm 落账边界。
 *
 * <p>本测试不连接任何数据库：结算计算只依赖 Mapper 读取，所有写入路径（settlement_confirm、ledger_txn、
 * order.status、reserved_amount、initial_shares、账户创建）都通过 Mockito 观察，确保 preview 阶段零写入、
 * confirm 阶段才产生唯一一套内部结算流水。</p>
 */
class SettlementServicePreviewTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 999L;
    private static final Long PRODUCT_ID = 5L;
    private static final Long SOURCE_ACCOUNT_ID = 100L;
    private static final Long TARGET_ACCOUNT_ID = 300L;
    private static final LocalDate CONFIRM_DATE = LocalDate.of(2026, 9, 28);
    private static final LocalDate NAV_DATE = LocalDate.of(2026, 9, 26);

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final SettlementConfirmMapper settlementConfirmMapper = mock(SettlementConfirmMapper.class);
    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final LedgerService ledgerService = mock(LedgerService.class);
    private final OrderFundingLineMapper orderFundingLineMapper = mock(OrderFundingLineMapper.class);
    private final UserService userService = mock(UserService.class);
    private final AccountService accountService = mock(AccountService.class);
    private final ProductMasterMapper productMasterMapper = mock(ProductMasterMapper.class);
    private final BrokerFeeService brokerFeeService = mock(BrokerFeeService.class);

    private final SettlementService service = new SettlementService(orderMapper, settlementConfirmMapper, accountMapper,
            ledgerService, orderFundingLineMapper, userService, accountService, productMasterMapper, brokerFeeService);

    @BeforeEach
    void stubDefaults() {
        when(userService.getCurrentUser()).thenReturn(currentUser(USER_ID, null));
        when(settlementConfirmMapper.selectByOrderId(anyString())).thenReturn(null);
        when(accountMapper.selectVirtualAccountsByOwner(any(), any(), anyString())).thenReturn(new ArrayList<>());
        when(accountMapper.selectChildren(anyLong())).thenReturn(new ArrayList<>());
        when(brokerFeeService.findBrokerAccountId(any())).thenReturn(null);
    }

    // ===================== 一、输入与权限阻断 =====================

    @Test
    void missingOrderIsBlocked() {
        when(orderMapper.selectByOrderId("ORD-X")).thenReturn(null);

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-X", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        assertFalse(preview.isConfirmSupported());
        assertTrue(hasReason(preview, "订单不存在"));
        verifyNoInteractions(orderFundingLineMapper, ledgerService);
    }

    @Test
    void nonPendingOrderIsBlocked() {
        when(orderMapper.selectByOrderId("ORD-1")).thenReturn(order("ORD-1", "BUY", "CONFIRMED", USER_ID));

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        assertFalse(preview.isConfirmSupported());
        assertTrue(hasReason(preview, "只有 PENDING"));
    }

    @Test
    void alreadySettledOrderIsBlocked() {
        when(orderMapper.selectByOrderId("ORD-1")).thenReturn(order("ORD-1", "BUY", "PENDING", USER_ID));
        when(settlementConfirmMapper.selectByOrderId("ORD-1")).thenReturn(existingConfirm("ORD-1"));

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        assertFalse(preview.isConfirmSupported());
        assertTrue(hasReason(preview, "已存在结算确认记录"));
        verifyNoInteractions(ledgerService);
    }

    @Test
    void foreignOrderIsBlocked() {
        when(orderMapper.selectByOrderId("ORD-1")).thenReturn(order("ORD-1", "BUY", "PENDING", OTHER_USER_ID));
        when(orderFundingLineMapper.selectByOrderId("ORD-1"))
                .thenReturn(List.of(line("ORD-1", 1, 900L, "1000.00", null, "SOURCE")));
        when(accountMapper.selectById(900L))
                .thenReturn(account(900L, "ACC-900", "他人银行卡", "BANK", OTHER_USER_ID, "5000.00", "1000.00"));
        when(productMasterMapper.selectById(PRODUCT_ID)).thenReturn(product());

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        assertFalse(preview.isConfirmSupported());
        assertTrue(hasReason(preview, "不属于当前用户"));
    }

    @Test
    void foreignFundingLineIsBlocked() {
        when(orderMapper.selectByOrderId("ORD-1")).thenReturn(order("ORD-1", "BUY", "PENDING", USER_ID));
        when(orderFundingLineMapper.selectByOrderId("ORD-1")).thenReturn(List.of(
                line("ORD-1", 1, SOURCE_ACCOUNT_ID, "600.00", null, "SOURCE"),
                line("ORD-1", 2, 200L, "400.00", null, "SOURCE")));
        when(accountMapper.selectById(SOURCE_ACCOUNT_ID))
                .thenReturn(account(SOURCE_ACCOUNT_ID, "ACC-100", "生活卡", "BANK", USER_ID, "5000.00", "1000.00"));
        when(accountMapper.selectById(200L))
                .thenReturn(account(200L, "ACC-200", "他人账户", "BANK", OTHER_USER_ID, "5000.00", "400.00"));
        when(productMasterMapper.selectById(PRODUCT_ID)).thenReturn(product());

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        assertFalse(preview.isConfirmSupported());
        assertTrue(hasReason(preview, "资金来源账户不在当前用户"));
    }

    @Test
    void zeroNavIsBlocked() {
        stubBuyPendingOrder("ORD-1");

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", CONFIRM_DATE, NAV_DATE, BigDecimal.ZERO, null, null, new BigDecimal("5"));

        assertFalse(preview.isConfirmSupported());
        assertTrue(hasReason(preview, "实际净值必须大于 0"));
    }

    @Test
    void negativeFeeIsBlocked() {
        stubBuyPendingOrder("ORD-1");

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("-1"));

        assertFalse(preview.isConfirmSupported());
        assertTrue(hasReason(preview, "手续费不能为负数"));
    }

    @Test
    void sellWithoutAmountIsBlocked() {
        stubSellLikeOrder("ORD-SELL", "SELL");

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-SELL", CONFIRM_DATE, NAV_DATE, new BigDecimal("5.0"), new BigDecimal("100.000000"), null, null);

        assertFalse(preview.isConfirmSupported());
        assertTrue(hasReason(preview, "必须填写大于 0 的实际确认金额"));
    }

    @Test
    void missingConfirmDateIsBlocked() {
        stubBuyPendingOrder("ORD-1");

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", null, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        assertFalse(preview.isConfirmSupported());
        assertTrue(hasReason(preview, "确认日期不能为空"));
    }

    // ===================== 二、preview 只读 =====================

    @Test
    void previewPerformsNoWrites() {
        stubBuyPendingOrder("ORD-1");

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        assertTrue(preview.isConfirmSupported());
        verify(settlementConfirmMapper, never()).insert(any());
        verify(orderMapper, never()).update(any());
        verify(orderMapper, never()).insert(any());
        verify(accountMapper, never()).insert(any());
        verify(accountMapper, never()).update(any());
        verify(accountMapper, never()).updateBalance(anyLong(), any());
        verify(accountMapper, never()).updateReservedAmount(anyLong(), any());
        verify(accountMapper, never()).updateInitialShares(anyLong(), any());
        verifyNoInteractions(ledgerService, accountService);
    }

    @Test
    void previewDoesNotMutateOrderReservedOrInitialShares() {
        Order pendingOrder = order("ORD-1", "BUY", "PENDING", USER_ID);
        Account cash = account(SOURCE_ACCOUNT_ID, "ACC-100", "生活卡", "BANK", USER_ID, "5000.00", "1000.00");
        when(orderMapper.selectByOrderId("ORD-1")).thenReturn(pendingOrder);
        when(orderFundingLineMapper.selectByOrderId("ORD-1"))
                .thenReturn(List.of(line("ORD-1", 1, SOURCE_ACCOUNT_ID, "1000.00", null, "SOURCE")));
        when(productMasterMapper.selectById(PRODUCT_ID)).thenReturn(product());
        when(accountMapper.selectById(SOURCE_ACCOUNT_ID)).thenReturn(cash);

        service.previewSettlement("ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        assertEquals("PENDING", pendingOrder.getStatus());
        assertEquals(0, new BigDecimal("1000.00").compareTo(cash.getReservedAmount()));
        assertNull(cash.getInitialShares());
    }

    // ===================== 三、四类订单预览语义 =====================

    @Test
    void buyPreviewDoesNotDeductOrderCashAgain() {
        stubBuyPendingOrder("ORD-1");

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        assertFalse(preview.isWillChangeCash());
        assertNull(posting(preview, "CASH", "DEBIT"));
        assertNull(posting(preview, "CASH", "CREDIT"));
    }

    @Test
    void buyPreviewShowsReceivableToPositionAndFee() {
        stubBuyPendingOrder("ORD-1");

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        assertTrue(preview.isConfirmSupported());
        assertTrue(preview.isWillChangeHolding());
        assertTrue(preview.isWillCreateSettlementConfirm());
        assertTrue(preview.isWillCreateLedgerTxn());

        SettlementPostingPreviewDTO receivable = posting(preview, "RECEIVABLE", "CREDIT");
        assertNotNull(receivable);
        assertEquals(0, new BigDecimal("1000.00").compareTo(receivable.getAmount()));
        assertTrue(receivable.getDescription().contains("待结算应收"));

        SettlementPostingPreviewDTO position = posting(preview, "POSITION", "DEBIT");
        assertNotNull(position);
        assertEquals(0, new BigDecimal("497.5").compareTo(position.getShares()));
        assertEquals(0, new BigDecimal("995.00").compareTo(position.getAmount()));
        assertTrue(position.getDescription().contains("份额 +"));

        SettlementPostingPreviewDTO fee = posting(preview, "FEE", "DEBIT");
        assertNotNull(fee);
        assertEquals(0, new BigDecimal("5").compareTo(fee.getAmount()));

        assertEquals(0, new BigDecimal("497.5").compareTo(preview.getComputedShares()));
        assertEquals(0, new BigDecimal("995.00").compareTo(preview.getComputedAmount()));
        assertTrue(summaryContains(preview, "不会再扣同一笔下单现金"));
        assertNotNull(preview.getFreshPreviewToken());
    }

    @Test
    void subscriptionPreviewShowsReceivableToLinkedAccountCashAndFee() {
        Order subscription = order("ORD-SUB", "SUBSCRIPTION", "PENDING", USER_ID);
        subscription.setAmount(new BigDecimal("1000.00"));
        Account linked = account(TARGET_ACCOUNT_ID, "ACC-300", "稳利宝", "MMF", USER_ID, "0.00", "0.00");
        when(orderMapper.selectByOrderId("ORD-SUB")).thenReturn(subscription);
        when(orderFundingLineMapper.selectByOrderId("ORD-SUB")).thenReturn(List.of(
                line("ORD-SUB", 1, SOURCE_ACCOUNT_ID, "1000.00", null, "SOURCE"),
                line("ORD-SUB", 2, TARGET_ACCOUNT_ID, null, null, "TARGET")));
        when(productMasterMapper.selectById(PRODUCT_ID)).thenReturn(product());
        when(accountMapper.selectById(SOURCE_ACCOUNT_ID))
                .thenReturn(account(SOURCE_ACCOUNT_ID, "ACC-100", "生活卡", "BANK", USER_ID, "5000.00", "1000.00"));
        when(accountMapper.selectById(TARGET_ACCOUNT_ID)).thenReturn(linked);
        when(accountMapper.selectByLinkedProductId(PRODUCT_ID)).thenReturn(linked);

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-SUB", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        assertTrue(preview.isConfirmSupported());
        assertTrue(preview.isWillChangeCash());
        assertTrue(preview.isWillChangeHolding());

        SettlementPostingPreviewDTO receivable = posting(preview, "RECEIVABLE", "CREDIT");
        assertNotNull(receivable);
        assertEquals(0, new BigDecimal("1000.00").compareTo(receivable.getAmount()));

        SettlementPostingPreviewDTO cash = posting(preview, "CASH", "DEBIT");
        assertNotNull(cash);
        assertEquals(0, new BigDecimal("995.00").compareTo(cash.getAmount()));

        SettlementPostingPreviewDTO fee = posting(preview, "FEE", "DEBIT");
        assertNotNull(fee);
        assertEquals(0, new BigDecimal("5").compareTo(fee.getAmount()));
    }

    @Test
    void sellPreviewShowsCashPositionAndFee() {
        stubSellLikeOrder("ORD-SELL", "SELL");

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-SELL", CONFIRM_DATE, NAV_DATE, new BigDecimal("5.0"),
                new BigDecimal("100.000000"), new BigDecimal("500.00"), new BigDecimal("2"));

        assertTrue(preview.isConfirmSupported());
        assertTrue(preview.isWillChangeCash());
        assertTrue(preview.isWillChangeHolding());

        SettlementPostingPreviewDTO cash = posting(preview, "CASH", "DEBIT");
        assertNotNull(cash);
        assertEquals(0, new BigDecimal("498.00").compareTo(cash.getAmount()));
        assertEquals(0, new BigDecimal("2").compareTo(preview.getConfirmFee()));

        SettlementPostingPreviewDTO position = posting(preview, "POSITION", "CREDIT");
        assertNotNull(position);
        assertEquals(0, new BigDecimal("500.00").compareTo(position.getAmount()));
        assertEquals(0, new BigDecimal("100.000000").compareTo(position.getShares()));

        SettlementPostingPreviewDTO fee = posting(preview, "FEE", "DEBIT");
        assertNotNull(fee);
        assertEquals(0, new BigDecimal("2").compareTo(fee.getAmount()));
        assertTrue(summaryContains(preview, "PENDING 占用失效"));
    }

    @Test
    void redemptionPreviewShowsCashPositionAndFee() {
        stubSellLikeOrder("ORD-RED", "REDEMPTION");

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-RED", CONFIRM_DATE, NAV_DATE, new BigDecimal("5.0"),
                new BigDecimal("100.000000"), new BigDecimal("500.00"), BigDecimal.ZERO);

        assertTrue(preview.isConfirmSupported());
        assertEquals("赎回", preview.getOrderTypeLabel());
        assertNotNull(posting(preview, "CASH", "DEBIT"));
        assertNotNull(posting(preview, "POSITION", "CREDIT"));
        assertNull(posting(preview, "FEE", "DEBIT"));
        assertTrue(preview.isWillChangeCash());
        assertTrue(preview.isWillChangeHolding());
    }

    @Test
    void allFourOrderTypesProduceSupportedPreview() {
        stubBuyPendingOrder("ORD-1");
        assertTrue(service.previewSettlement("ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null,
                new BigDecimal("5")).isConfirmSupported());

        stubSellLikeOrder("ORD-SELL", "SELL");
        assertTrue(service.previewSettlement("ORD-SELL", CONFIRM_DATE, NAV_DATE, new BigDecimal("5.0"),
                new BigDecimal("100.000000"), new BigDecimal("500.00"), new BigDecimal("2")).isConfirmSupported());

        stubSellLikeOrder("ORD-RED", "REDEMPTION");
        assertTrue(service.previewSettlement("ORD-RED", CONFIRM_DATE, NAV_DATE, new BigDecimal("5.0"),
                new BigDecimal("100.000000"), new BigDecimal("500.00"), new BigDecimal("2")).isConfirmSupported());
    }

    // ===================== 四、fresh preview 令牌 =====================

    @Test
    void tokenInvalidatedWhenOrderChanges() {
        Order pendingOrder = order("ORD-1", "BUY", "PENDING", USER_ID);
        stubBuyPendingOrder("ORD-1", pendingOrder);
        stubCreatedVirtualAccount();

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));
        assertTrue(preview.isConfirmSupported());

        pendingOrder.setUpdatedAt(LocalDateTime.of(2026, 9, 28, 11, 0));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.confirmSettlement("ORD-1",
                CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"),
                preview.getFreshPreviewToken()));
        assertTrue(ex.getMessage().contains("结算预览已失效"));
        verify(settlementConfirmMapper, never()).insert(any());
        verifyNoInteractions(ledgerService);
    }

    @Test
    void tokenInvalidatedWhenFundingLineChanges() {
        OrderFundingLine fundingLine = line("ORD-1", 1, SOURCE_ACCOUNT_ID, "1000.00", null, "SOURCE");
        stubBuyPendingOrder("ORD-1", order("ORD-1", "BUY", "PENDING", USER_ID), new ArrayList<>(List.of(fundingLine)));

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        fundingLine.setAmount(new BigDecimal("900.00"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.confirmSettlement("ORD-1",
                CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"),
                preview.getFreshPreviewToken()));
        assertTrue(ex.getMessage().contains("结算预览已失效"));
        verify(settlementConfirmMapper, never()).insert(any());
    }

    @Test
    void tokenInvalidatedWhenInputChanges() {
        stubBuyPendingOrder("ORD-1");

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.confirmSettlement("ORD-1",
                CONFIRM_DATE, NAV_DATE, new BigDecimal("2.5"), null, null, new BigDecimal("5"),
                preview.getFreshPreviewToken()));
        assertTrue(ex.getMessage().contains("结算预览已失效"));
        verify(settlementConfirmMapper, never()).insert(any());
    }

    @Test
    void confirmWithoutTokenIsBlocked() {
        stubBuyPendingOrder("ORD-1");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.confirmSettlement("ORD-1",
                CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"), null));
        assertTrue(ex.getMessage().contains("缺少 fresh preview 令牌"));
        verify(settlementConfirmMapper, never()).insert(any());
    }

    // ===================== 五、confirm 落账与幂等 =====================

    @Test
    void confirmWithFreshTokenWritesSingleLedgerSet() {
        Order pendingOrder = order("ORD-1", "BUY", "PENDING", USER_ID);
        stubBuyPendingOrder("ORD-1", pendingOrder);
        stubCreatedVirtualAccount();

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        SettlementConfirm result = service.confirmSettlement("ORD-1", CONFIRM_DATE, NAV_DATE,
                new BigDecimal("2.0"), null, null, new BigDecimal("5"), preview.getFreshPreviewToken());

        assertNotNull(result);
        assertEquals("ORD-1", result.getOrderId());
        assertEquals("CONFIRMED", pendingOrder.getStatus());
        verify(settlementConfirmMapper, times(1)).insert(any());
        verify(orderMapper, times(1)).update(any());
        verify(accountMapper, times(1)).updateReservedAmount(eq(SOURCE_ACCOUNT_ID), any());
        verify(ledgerService, times(1)).createTransaction(any(), any(), any(), any(), any(), any(), any(), any(),
                anyBoolean(), any(), any());
    }

    @Test
    void duplicateConfirmIsIdempotent() {
        SettlementConfirm existing = existingConfirm("ORD-1");
        when(settlementConfirmMapper.selectByOrderId("ORD-1")).thenReturn(existing);

        SettlementConfirm result = service.confirmSettlement("ORD-1", CONFIRM_DATE, NAV_DATE,
                new BigDecimal("2.0"), null, null, new BigDecimal("5"), "stale-token");

        assertSame(existing, result);
        verify(settlementConfirmMapper, never()).insert(any());
        verify(orderMapper, never()).update(any());
        verifyNoInteractions(ledgerService, accountService);
    }

    @Test
    void confirmPropagatesLedgerFailureWithoutPartialOrderCommit() {
        Order pendingOrder = order("ORD-1", "BUY", "PENDING", USER_ID);
        stubBuyPendingOrder("ORD-1", pendingOrder);
        stubCreatedVirtualAccount();
        when(ledgerService.createTransaction(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                any(), any())).thenThrow(new RuntimeException("ledger-down"));

        SettlementPreviewDTO preview = service.previewSettlement(
                "ORD-1", CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.confirmSettlement("ORD-1",
                CONFIRM_DATE, NAV_DATE, new BigDecimal("2.0"), null, null, new BigDecimal("5"),
                preview.getFreshPreviewToken()));

        assertEquals("ledger-down", ex.getMessage());
        assertEquals("PENDING", pendingOrder.getStatus());
        verify(orderMapper, never()).update(any());
    }

    // ===================== 测试支撑 =====================

    private void stubBuyPendingOrder(String orderId) {
        stubBuyPendingOrder(orderId, order(orderId, "BUY", "PENDING", USER_ID));
    }

    private void stubBuyPendingOrder(String orderId, Order pendingOrder) {
        stubBuyPendingOrder(orderId, pendingOrder,
                new ArrayList<>(List.of(line(orderId, 1, SOURCE_ACCOUNT_ID, "1000.00", null, "SOURCE"))));
    }

    private void stubBuyPendingOrder(String orderId, Order pendingOrder, List<OrderFundingLine> fundingLines) {
        when(orderMapper.selectByOrderId(orderId)).thenReturn(pendingOrder);
        when(orderFundingLineMapper.selectByOrderId(orderId)).thenReturn(fundingLines);
        when(productMasterMapper.selectById(PRODUCT_ID)).thenReturn(product());
        when(accountMapper.selectById(SOURCE_ACCOUNT_ID))
                .thenReturn(account(SOURCE_ACCOUNT_ID, "ACC-100", "生活卡", "BANK", USER_ID, "5000.00", "1000.00"));
    }

    private void stubSellLikeOrder(String orderId, String orderType) {
        Order sell = order(orderId, orderType, "PENDING", USER_ID);
        sell.setAmount(new BigDecimal("500.00"));
        sell.setShares(new BigDecimal("100.000000"));
        when(orderMapper.selectByOrderId(orderId)).thenReturn(sell);
        when(orderFundingLineMapper.selectByOrderId(orderId)).thenReturn(new ArrayList<>(List.of(
                line(orderId, 1, SOURCE_ACCOUNT_ID, null, "100.000000", "SOURCE"),
                line(orderId, 2, TARGET_ACCOUNT_ID, null, null, "TARGET"))));
        when(productMasterMapper.selectById(PRODUCT_ID)).thenReturn(product());
        when(accountMapper.selectById(SOURCE_ACCOUNT_ID))
                .thenReturn(account(SOURCE_ACCOUNT_ID, "ACC-100", "华宝证券", "BROKER", USER_ID, "0.00", "0.00"));
        when(accountMapper.selectById(TARGET_ACCOUNT_ID))
                .thenReturn(account(TARGET_ACCOUNT_ID, "ACC-300", "到账银行卡", "BANK", USER_ID, "0.00", "0.00"));
    }

    private void stubCreatedVirtualAccount() {
        when(accountService.getOrCreateVirtualAccount(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Account created = new Account();
                    created.setId(500L);
                    created.setAccountCode("VIRTUAL-CREATED");
                    created.setAccountName("自动创建账户");
                    created.setAccountKind("VIRTUAL");
                    created.setCurrency("CNY");
                    return created;
                });
    }

    private static boolean hasReason(SettlementPreviewDTO preview, String fragment) {
        return preview.getBlockingReasons().stream().anyMatch(reason -> reason.contains(fragment));
    }

    private static boolean summaryContains(SettlementPreviewDTO preview, String fragment) {
        return preview.getSummaryLines().stream().anyMatch(line -> line.contains(fragment));
    }

    private static SettlementPostingPreviewDTO posting(SettlementPreviewDTO preview, String accountType, String postingType) {
        for (SettlementPostingPreviewDTO row : preview.getPostingsPreview()) {
            if (accountType.equals(row.getAccountType()) && postingType.equals(row.getPostingType())) {
                return row;
            }
        }
        return null;
    }

    private static AuthResponse.UserInfo currentUser(Long id, Long familyId) {
        AuthResponse.UserInfo info = new AuthResponse.UserInfo();
        info.setId(id);
        info.setFamilyId(familyId);
        info.setUsername("tester");
        return info;
    }

    private static Order order(String orderId, String orderType, String status, Long userId) {
        Order order = new Order();
        order.setId(1L);
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setProductId(PRODUCT_ID);
        order.setOrderType(orderType);
        order.setStatus(status);
        order.setAmount(new BigDecimal("1000.00"));
        order.setShares(new BigDecimal("100.000000"));
        order.setRequestedAt(LocalDateTime.of(2026, 9, 20, 10, 0));
        order.setUpdatedAt(LocalDateTime.of(2026, 9, 20, 10, 0));
        return order;
    }

    private static OrderFundingLine line(String orderId, int lineNo, Long accountId, String amount, String shares,
                                        String lineType) {
        OrderFundingLine line = new OrderFundingLine();
        line.setOrderId(orderId);
        line.setLineNo(lineNo);
        line.setAccountId(accountId);
        line.setAmount(amount == null ? null : new BigDecimal(amount));
        line.setShares(shares == null ? null : new BigDecimal(shares));
        line.setCurrency("CNY");
        line.setLineType(lineType);
        return line;
    }

    private static Account account(Long id, String code, String name, String type, Long ownerUserId, String balance,
                                   String reserved) {
        Account account = new Account();
        account.setId(id);
        account.setAccountCode(code);
        account.setAccountName(name);
        account.setAccountKind("REAL");
        account.setAccountType(type);
        account.setOwnerType("PERSONAL");
        account.setOwnerUserId(ownerUserId);
        account.setCurrency("CNY");
        account.setBalance(new BigDecimal(balance));
        account.setReservedAmount(new BigDecimal(reserved));
        account.setInitialBalance(BigDecimal.ZERO);
        account.setIsActive(true);
        account.setIsFixedAmount(false);
        return account;
    }

    private static ProductMaster product() {
        ProductMaster product = new ProductMaster();
        product.setId(PRODUCT_ID);
        product.setProductCode("510300");
        product.setProductName("沪深300ETF");
        product.setAssetType("ETF");
        product.setChannel("EXCHANGE");
        product.setMarket("SH");
        product.setCurrency("CNY");
        product.setIsActive(true);
        return product;
    }

    private static SettlementConfirm existingConfirm(String orderId) {
        SettlementConfirm confirm = new SettlementConfirm();
        confirm.setId(77L);
        confirm.setOrderId(orderId);
        confirm.setConfirmNav(new BigDecimal("2.0"));
        return confirm;
    }
}