package com.timelordtty.dca.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.DraftLedgerEntryDTO;
import com.timelordtty.dca.dto.DraftPreviewDTO;
import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.DraftLedgerEntryMapper;
import com.timelordtty.dca.mapper.ProductMasterMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.DraftLedgerEntry;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.model.ProductMaster;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * v0.12 投资卖出 / 赎回草稿闭环：只验证只读预览规则与“二次确认后才创建 PENDING 订单”的安全边界。
 *
 * <p>本测试不连接任何数据库：所有写入路径都通过 Mockito 观察，确保 preview 只读、confirm 复用
 * OrderService 只登记 SOURCE / TARGET 份额占用，且不调用 SettlementService、不生成最终持仓、
 * 不改现金余额、不重复创建订单。</p>
 */
class DraftLedgerEntryServiceSellRedeemTest {

    private final DraftLedgerEntryMapper mapper = mock(DraftLedgerEntryMapper.class);
    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final QuickEntryService quickEntryService = mock(QuickEntryService.class);
    private final ProductMasterMapper productMasterMapper = mock(ProductMasterMapper.class);
    private final OrderService orderService = mock(OrderService.class);
    private final HoldingService holdingService = mock(HoldingService.class);
    private final DraftLedgerEntryService service = new DraftLedgerEntryService(
            mapper, accountMapper, quickEntryService, new ObjectMapper(), productMasterMapper, orderService, holdingService);

    @BeforeEach
    void stubDefaults() {
        // 默认所有账户都是叶子账户；需要父账户场景的用例再单独覆盖 selectChildren。
        lenient().when(accountMapper.selectChildren(anyLong())).thenReturn(List.of());
        // 默认没有 PENDING 占用；需要观察可用份额扣减的用例再单独覆盖。
        lenient().when(orderService.sumPendingSellSharesByAccount(anyLong(), anyLong(), anyLong()))
                .thenReturn(BigDecimal.ZERO);
    }

    @Test
    void sellPreviewMissingSharesCannotConfirm() {
        bindDraft("{\"txnType\":\"SELL\",\"productId\":5,\"sourceAccountId\":7,\"targetAccountId\":8}");
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "1000")));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "到账银行卡", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("shares"));
        assertFalse(preview.getWillCreateOrder());
        assertEquals("SELL", preview.getOrderType());
    }

    @Test
    void redemptionPreviewMissingSourceAccountCannotConfirm() {
        bindDraft("{\"txnType\":\"REDEMPTION\",\"productId\":5,\"shares\":1000,\"targetAccountId\":8}");
        stubActiveProduct();
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "到账银行卡", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("sourceAccountId"));
        assertEquals("REDEMPTION", preview.getOrderType());
    }

    @Test
    void sellPreviewMissingTargetAccountCannotConfirm() {
        bindDraft("{\"txnType\":\"SELL\",\"productId\":5,\"shares\":500,\"sourceAccountId\":7}");
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "1000")));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("targetAccountId"));
    }

    @Test
    void sellPreviewMissingProductCannotConfirm() {
        bindDraft("{\"txnType\":\"SELL\",\"shares\":500,\"sourceAccountId\":7,\"targetAccountId\":8}");
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "到账银行卡", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("productId"));
        verifyNoInteractions(holdingService);
    }

    @Test
    void nonPositiveSharesBlocksConfirm() {
        bindDraft("{\"txnType\":\"SELL\",\"productId\":5,\"shares\":0,\"sourceAccountId\":7,\"targetAccountId\":8}");
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "1000")));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "到账银行卡", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("shares"));
        assertEquals("NONE", preview.getImpactDirection());
    }

    @Test
    void inactiveProductBlocksConfirm() {
        bindDraft("{\"txnType\":\"REDEMPTION\",\"productId\":5,\"shares\":500,\"sourceAccountId\":7,\"targetAccountId\":8}");
        ProductMaster inactive = activeProduct(5L, "FUND", "CNY");
        inactive.setIsActive(false);
        when(productMasterMapper.selectById(5L)).thenReturn(inactive);
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "到账银行卡", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("productId"));
    }

    @Test
    void accountWithoutRealHoldingBlocksConfirm() {
        bindDraft("{\"txnType\":\"SELL\",\"productId\":5,\"shares\":500,\"sourceAccountId\":7,\"targetAccountId\":8}");
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of());
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "到账银行卡", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("sourceAccountId"));
        assertNull(preview.getAvailableShares());
    }

    @Test
    void sharesOverAvailableBlocksConfirm() {
        bindDraft("{\"txnType\":\"SELL\",\"productId\":5,\"shares\":500,\"sourceAccountId\":7,\"targetAccountId\":8}");
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "300")));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "到账银行卡", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("shares"));
        assertEquals(0, new BigDecimal("300").compareTo(preview.getAvailableShares()));
    }

    @Test
    void pendingOccupiedSharesReduceAvailability() {
        bindDraft("{\"txnType\":\"REDEMPTION\",\"productId\":5,\"shares\":300,\"sourceAccountId\":7,\"targetAccountId\":8}");
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "1000")));
        when(orderService.sumPendingSellSharesByAccount(5L, 10L, 7L)).thenReturn(new BigDecimal("800"));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "到账银行卡", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("shares"));
        assertEquals(0, new BigDecimal("200").compareTo(preview.getAvailableShares()));
        assertEquals(0, new BigDecimal("300").compareTo(preview.getShares()));
    }

    @Test
    void fullyPendingOccupiedSharesBlockAnySell() {
        bindDraft("{\"txnType\":\"SELL\",\"productId\":5,\"shares\":1,\"sourceAccountId\":7,\"targetAccountId\":8}");
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "1000")));
        when(orderService.sumPendingSellSharesByAccount(5L, 10L, 7L)).thenReturn(new BigDecimal("1000"));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "到账银行卡", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("sourceAccountId"));
        assertEquals(0, BigDecimal.ZERO.compareTo(preview.getAvailableShares()));
    }

    @Test
    void positionTargetAccountBlocksConfirm() {
        bindDraft("{\"txnType\":\"SELL\",\"productId\":5,\"shares\":500,\"sourceAccountId\":7,\"targetAccountId\":8}");
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "1000")));
        Account target = cashAccount(8L, "证券持仓账户", "CNY");
        target.setAccountType("POSITION");
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(target);

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("targetAccountId"));
    }

    @Test
    void parentTargetAccountBlocksConfirm() {
        bindDraft("{\"txnType\":\"SELL\",\"productId\":5,\"shares\":500,\"sourceAccountId\":7,\"targetAccountId\":8}");
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "1000")));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "银行父账户", "CNY"));
        when(accountMapper.selectChildren(8L)).thenReturn(List.of(new Account()));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("targetAccountId"));
    }

    @Test
    void invisibleTargetAccountBlocksConfirm() {
        bindDraft("{\"txnType\":\"SELL\",\"productId\":5,\"shares\":500,\"sourceAccountId\":7,\"targetAccountId\":8}");
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "1000")));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(null);

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("targetAccountId"));
    }

    @Test
    void currencyMismatchTargetBlocksConfirm() {
        bindDraft("{\"txnType\":\"SELL\",\"productId\":5,\"shares\":500,\"sourceAccountId\":7,\"targetAccountId\":8}");
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "1000")));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "美元账户", "USD"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("targetAccountId"));
    }

    @Test
    void sellPreviewIsReadOnlyAndOnlyWritesDraftPreviewPayload() {
        bindDraft("{\"txnType\":\"SELL\",\"productId\":5,\"shares\":500,\"sourceAccountId\":7,\"targetAccountId\":8}");
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "1000")));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "到账银行卡", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertTrue(preview.getConfirmSupported());
        assertEquals("SELL", preview.getOrderType());
        assertEquals("纳指ETF", preview.getProductName());
        assertEquals("513100", preview.getProductCode());
        assertEquals(0, new BigDecimal("1000").compareTo(preview.getAvailableShares()));
        assertEquals(0, new BigDecimal("500").compareTo(preview.getShares()));
        assertEquals(0, new BigDecimal("500").compareTo(preview.getRemainingShares()));
        assertTrue(preview.getWillCreateOrder());
        assertFalse(preview.getWillCreateLedgerTxn());
        assertFalse(preview.getWillCreateSettlement());
        assertFalse(preview.getWillAffectHolding());
        assertEquals("NONE", preview.getImpactDirection());
        assertEquals(0, BigDecimal.ZERO.compareTo(preview.getAccountDelta()));
        assertEquals(0, BigDecimal.ZERO.compareTo(preview.getTargetAccountDelta()));
        assertEquals(0, BigDecimal.ZERO.compareTo(preview.getReceivableDelta()));
        assertTrue(preview.getSharesMessage().contains("不会立即减少持仓"));
        // preview 只写草稿预览载荷，绝不创建订单、绝不写正式账本。
        verify(mapper).updatePreview(eq(1L), anyString());
        verify(orderService, never()).createSellRedeemDraftOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(orderService, never()).createInvestmentDraftOrder(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void confirmSellCreatesSinglePendingOrderWithoutTouchingCashOrHolding() {
        DraftLedgerEntry draft = bindDraft("{\"txnType\":\"SELL\",\"productId\":5,\"shares\":500,\"sourceAccountId\":7,\"targetAccountId\":8}");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "1000")));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "到账银行卡", "CNY"));
        when(orderService.createSellRedeemDraftOrder(eq(10L), eq(20L), eq(5L), eq("SELL"),
                eq(new BigDecimal("500")), eq(7L), eq(8L), any(), any(), any())).thenReturn(pendingOrder("ORD-SELL-1"));
        when(mapper.markConfirmed(1L, null, "ORD-SELL-1")).thenReturn(1);

        DraftLedgerEntryDTO result = service.confirmDraft(10L, 20L, 1L);

        assertEquals(1L, result.getId());
        verify(orderService).createSellRedeemDraftOrder(eq(10L), eq(20L), eq(5L), eq("SELL"),
                eq(new BigDecimal("500")), eq(7L), eq(8L), any(), any(), any());
        verify(mapper).markConfirmed(1L, null, "ORD-SELL-1");
        verify(orderService, never()).createInvestmentDraftOrder(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void confirmRedemptionCreatesSinglePendingOrder() {
        DraftLedgerEntry draft = bindDraft("{\"txnType\":\"REDEMPTION\",\"productId\":5,\"shares\":1000,\"sourceAccountId\":7,\"targetAccountId\":8}");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "1500")));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "到账银行卡", "CNY"));
        when(orderService.createSellRedeemDraftOrder(eq(10L), eq(20L), eq(5L), eq("REDEMPTION"),
                eq(new BigDecimal("1000")), eq(7L), eq(8L), any(), any(), any())).thenReturn(pendingOrder("ORD-REDEEM-1"));
        when(mapper.markConfirmed(1L, null, "ORD-REDEEM-1")).thenReturn(1);

        service.confirmDraft(10L, 20L, 1L);

        verify(orderService).createSellRedeemDraftOrder(eq(10L), eq(20L), eq(5L), eq("REDEMPTION"),
                eq(new BigDecimal("1000")), eq(7L), eq(8L), any(), any(), any());
        verify(mapper).markConfirmed(1L, null, "ORD-REDEEM-1");
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void confirmRejectsFreshPreviewWhenSharesExceedAvailability() {
        DraftLedgerEntry draft = bindDraft("{\"txnType\":\"SELL\",\"productId\":5,\"shares\":5000,\"sourceAccountId\":7,\"targetAccountId\":8}");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "1000")));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "到账银行卡", "CNY"));

        assertThrows(RuntimeException.class, () -> service.confirmDraft(10L, 20L, 1L));

        verify(orderService, never()).createSellRedeemDraftOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(mapper, never()).markConfirmed(any(), any(), any());
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void confirmingAlreadyConfirmedSellDraftIsIdempotent() {
        DraftLedgerEntry draft = new DraftLedgerEntry();
        draft.setId(1L);
        draft.setOwnerUserId(10L);
        draft.setOwnerFamilyId(20L);
        draft.setStatus("CONFIRMED");
        draft.setConfirmOrderId("ORD-SELL-EXISTING");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);

        DraftLedgerEntryDTO result = service.confirmDraft(10L, 20L, 1L);

        assertEquals(1L, result.getId());
        verifyNoInteractions(orderService);
        verifyNoInteractions(quickEntryService);
        verifyNoInteractions(productMasterMapper);
        verify(mapper, never()).markConfirmed(any(), any(), any());
    }

    @Test
    void failingMarkConfirmedRollsBackSellDraftToDraft() {
        DraftLedgerEntry draft = bindDraft("{\"txnType\":\"SELL\",\"productId\":5,\"shares\":500,\"sourceAccountId\":7,\"targetAccountId\":8}");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);
        stubActiveProduct();
        when(holdingService.getProductHoldingsByAccount(5L, 10L, 20L)).thenReturn(List.of(holding(7L, "券商账户", "1000")));
        when(accountMapper.selectVisibleRealById(8L, 10L, 20L)).thenReturn(cashAccount(8L, "到账银行卡", "CNY"));
        when(orderService.createSellRedeemDraftOrder(eq(10L), eq(20L), eq(5L), eq("SELL"),
                eq(new BigDecimal("500")), eq(7L), eq(8L), any(), any(), any())).thenReturn(pendingOrder("ORD-SELL-2"));
        when(mapper.markConfirmed(1L, null, "ORD-SELL-2")).thenReturn(0);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.confirmDraft(10L, 20L, 1L));

        assertTrue(error.getMessage().contains("回滚"));
        assertEquals("DRAFT", draft.getStatus());
        assertNull(draft.getConfirmOrderId());
    }

    private void stubActiveProduct() {
        when(productMasterMapper.selectById(5L)).thenReturn(activeProduct(5L, "ETF", "CNY"));
    }

    private DraftLedgerEntry bindDraft(String payloadJson) {
        DraftLedgerEntry draft = new DraftLedgerEntry();
        draft.setId(1L);
        draft.setOwnerUserId(10L);
        draft.setOwnerFamilyId(20L);
        draft.setStatus("DRAFT");
        draft.setParsedPayloadJson(payloadJson);
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);
        return draft;
    }

    private HoldingService.AccountHoldingInfo holding(Long accountId, String accountName, String shares) {
        HoldingService.AccountHoldingInfo info = new HoldingService.AccountHoldingInfo();
        info.setAccountId(accountId);
        info.setAccountName(accountName);
        info.setShares(new BigDecimal(shares));
        info.setMarketValue(new BigDecimal(shares));
        return info;
    }

    private ProductMaster activeProduct(Long id, String assetType, String currency) {
        ProductMaster product = new ProductMaster();
        product.setId(id);
        product.setProductName("纳指ETF");
        product.setProductCode("513100");
        product.setAssetType(assetType);
        product.setCurrency(currency);
        product.setIsActive(true);
        return product;
    }

    private Account cashAccount(Long id, String name, String currency) {
        Account account = new Account();
        account.setId(id);
        account.setAccountName(name);
        account.setAccountKind("REAL");
        account.setAccountType("BANK");
        account.setFundUsage("SPENDABLE");
        account.setCurrency(currency);
        account.setOwnerUserId(10L);
        account.setOwnerFamilyId(20L);
        account.setBalance(new BigDecimal("5000"));
        account.setReservedAmount(BigDecimal.ZERO);
        account.setIsActive(true);
        return account;
    }

    private Order pendingOrder(String orderId) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setStatus("PENDING");
        return order;
    }
}