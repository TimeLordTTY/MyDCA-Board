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
import static org.mockito.Mockito.times;
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
 * v0.11 投资买入 / 申购草稿闭环：只验证预览规则与“二次确认后才创建 PENDING 订单”的安全边界。
 *
 * <p>本测试不连接任何数据库：所有写入路径都通过 Mockito 观察，确保 preview 只读、confirm 复用 OrderService，
 * 且不调用 SettlementService、不生成最终持仓、不重复创建订单或付款账本。</p>
 */
class DraftLedgerEntryServiceInvestmentTest {

    private final DraftLedgerEntryMapper mapper = mock(DraftLedgerEntryMapper.class);
    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final QuickEntryService quickEntryService = mock(QuickEntryService.class);
    private final ProductMasterMapper productMasterMapper = mock(ProductMasterMapper.class);
    private final OrderService orderService = mock(OrderService.class);
    private final HoldingService holdingService = mock(HoldingService.class);
    private final DraftLedgerEntryService service = new DraftLedgerEntryService(
            mapper, accountMapper, quickEntryService, new ObjectMapper(), productMasterMapper, orderService, holdingService);

    @BeforeEach
    void stubDefaultAccountChildren() {
        // 默认所有账户都是叶子账户；需要父账户场景的用例再单独覆盖 selectChildren。
        lenient().when(accountMapper.selectChildren(anyLong())).thenReturn(List.of());
    }

    @Test
    void buyPreviewWithoutProductIdCannotConfirm() {
        bindDraft("{\"txnType\":\"BUY\",\"accountId\":7,\"amount\":1000}");
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(investableAccount(7L, "证券投资账户", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("productId"));
        assertFalse(preview.getWillCreateOrder());
        assertEquals("BUY", preview.getOrderType());
    }

    @Test
    void subscriptionPreviewWithoutProductIdCannotConfirm() {
        bindDraft("{\"txnType\":\"SUBSCRIPTION\",\"accountId\":7,\"amount\":500}");
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(investableAccount(7L, "证券投资账户", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("productId"));
    }

    @Test
    void missingAccountIdBlocksConfirm() {
        bindDraft("{\"txnType\":\"BUY\",\"productId\":5,\"amount\":1000}");
        when(productMasterMapper.selectById(5L)).thenReturn(activeProduct(5L, "ETF", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("accountId"));
    }

    @Test
    void nonPositiveAmountBlocksConfirm() {
        bindDraft("{\"txnType\":\"BUY\",\"productId\":5,\"accountId\":7,\"amount\":0}");
        when(productMasterMapper.selectById(5L)).thenReturn(activeProduct(5L, "ETF", "CNY"));
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(investableAccount(7L, "证券投资账户", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("amount"));
        assertEquals("NONE", preview.getImpactDirection());
    }

    @Test
    void missingProductBlocksConfirm() {
        bindDraft("{\"txnType\":\"BUY\",\"productId\":5,\"accountId\":7,\"amount\":1000}");
        when(productMasterMapper.selectById(5L)).thenReturn(null);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(investableAccount(7L, "证券投资账户", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("productId"));
    }

    @Test
    void inactiveProductBlocksConfirm() {
        bindDraft("{\"txnType\":\"SUBSCRIPTION\",\"productId\":5,\"accountId\":7,\"amount\":1000}");
        ProductMaster inactive = activeProduct(5L, "FUND", "CNY");
        inactive.setIsActive(false);
        when(productMasterMapper.selectById(5L)).thenReturn(inactive);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(investableAccount(7L, "证券投资账户", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("productId"));
    }

    @Test
    void invisibleOrVirtualAccountBlocksConfirm() {
        bindDraft("{\"txnType\":\"BUY\",\"productId\":5,\"accountId\":7,\"amount\":1000}");
        when(productMasterMapper.selectById(5L)).thenReturn(activeProduct(5L, "ETF", "CNY"));
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(null);

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("accountId"));
    }

    @Test
    void parentAccountBlocksConfirm() {
        bindDraft("{\"txnType\":\"BUY\",\"productId\":5,\"accountId\":7,\"amount\":1000}");
        when(productMasterMapper.selectById(5L)).thenReturn(activeProduct(5L, "ETF", "CNY"));
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(investableAccount(7L, "券商父账户", "CNY"));
        when(accountMapper.selectChildren(7L)).thenReturn(List.of(new Account()));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("accountId"));
    }

    @Test
    void currencyMismatchBlocksConfirm() {
        bindDraft("{\"txnType\":\"BUY\",\"productId\":5,\"accountId\":7,\"amount\":1000}");
        when(productMasterMapper.selectById(5L)).thenReturn(activeProduct(5L, "ETF", "USD"));
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(investableAccount(7L, "人民币账户", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("accountId"));
    }

    @Test
    void spendableAccountBlocksNormalInvestment() {
        bindDraft("{\"txnType\":\"BUY\",\"productId\":5,\"accountId\":7,\"amount\":1000}");
        when(productMasterMapper.selectById(5L)).thenReturn(activeProduct(5L, "ETF", "CNY"));
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(realAccount(7L, "生活费", "SPENDABLE", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMessage().contains("TRANSFER"));
    }

    @Test
    void reservedAccountBlocksNormalInvestment() {
        bindDraft("{\"txnType\":\"SUBSCRIPTION\",\"productId\":5,\"accountId\":7,\"amount\":1000}");
        when(productMasterMapper.selectById(5L)).thenReturn(activeProduct(5L, "FUND", "CNY"));
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(realAccount(7L, "房租专款", "RESERVED", "CNY"));

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertTrue(preview.getMissingFields().contains("accountId"));
    }

    @Test
    void investableAccountAllowsNormalInvestmentWithLedgerImpact() {
        bindDraft("{\"txnType\":\"BUY\",\"productId\":5,\"accountId\":7,\"amount\":1000}");
        when(productMasterMapper.selectById(5L)).thenReturn(activeProduct(5L, "ETF", "CNY"));
        Account account = investableAccount(7L, "证券投资账户", "CNY");
        account.setBalance(new BigDecimal("5000"));
        account.setReservedAmount(BigDecimal.ZERO);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account);

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertTrue(preview.getConfirmSupported());
        assertEquals("ETF", preview.getProductAssetType());
        assertEquals("纳指ETF", preview.getProductName());
        assertEquals("513100", preview.getProductCode());
        assertEquals(new BigDecimal("5000"), preview.getAvailableBefore());
        assertEquals(new BigDecimal("-1000"), preview.getAccountDelta());
        assertEquals(new BigDecimal("1000"), preview.getReceivableDelta());
        assertEquals("DECREASE", preview.getImpactDirection());
        assertTrue(preview.getWillCreateOrder());
        assertTrue(preview.getWillCreateLedgerTxn());
        assertFalse(preview.getWillCreateSettlement());
        assertFalse(preview.getWillAffectHolding());
        assertTrue(preview.getMessage().contains("待结算应收"));
        verify(mapper).updatePreview(eq(1L), anyString());
    }

    @Test
    void bondRepoWithReservedAccountIsAllowed() {
        bindDraft("{\"txnType\":\"BUY\",\"productId\":9,\"accountId\":7,\"amount\":1000}");
        when(productMasterMapper.selectById(9L)).thenReturn(activeProduct(9L, "BOND_REPO", "CNY"));
        Account account = realAccount(7L, "逆回购专款", "RESERVED", "CNY");
        account.setBalance(new BigDecimal("5000"));
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account);

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertTrue(preview.getConfirmSupported());
    }

    @Test
    void insufficientAvailableBalanceBlocksConfirm() {
        bindDraft("{\"txnType\":\"BUY\",\"productId\":5,\"accountId\":7,\"amount\":1000}");
        when(productMasterMapper.selectById(5L)).thenReturn(activeProduct(5L, "ETF", "CNY"));
        Account account = investableAccount(7L, "证券投资账户", "CNY");
        account.setBalance(new BigDecimal("900"));
        account.setReservedAmount(new BigDecimal("100"));
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(account);

        DraftPreviewDTO preview = service.previewDraft(10L, 20L, 1L);

        assertFalse(preview.getConfirmSupported());
        assertEquals(new BigDecimal("800"), preview.getAvailableBefore());
        assertTrue(preview.getMissingFields().contains("accountId"));
    }

    @Test
    void previewNeverCreatesOrderLedgerOrSettlement() {
        bindDraft("{\"txnType\":\"BUY\",\"productId\":5,\"accountId\":7,\"amount\":1000}");
        when(productMasterMapper.selectById(5L)).thenReturn(activeProduct(5L, "ETF", "CNY"));
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(investableAccount(7L, "证券投资账户", "CNY"));

        service.previewDraft(10L, 20L, 1L);

        verifyNoInteractions(orderService);
        verifyNoInteractions(quickEntryService);
        verify(mapper, never()).markConfirmed(any(), any(), any());
    }

    @Test
    void confirmBuyDraftCreatesSinglePendingOrderAndMarksDraftConfirmed() {
        DraftLedgerEntry draft = bindDraft("{\"txnType\":\"BUY\",\"productId\":5,\"accountId\":7,\"amount\":1000,\"note\":\"买入纳指ETF\"}");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);
        when(productMasterMapper.selectById(5L)).thenReturn(activeProduct(5L, "ETF", "CNY"));
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(investableAccount(7L, "证券投资账户", "CNY"));
        Order order = pendingOrder("ORD-BUY-1");
        when(orderService.createInvestmentDraftOrder(10L, 20L, 5L, "BUY", new BigDecimal("1000"), 7L, null, null, "买入纳指ETF"))
                .thenReturn(order);
        when(mapper.markConfirmed(1L, null, "ORD-BUY-1")).thenReturn(1);

        DraftLedgerEntryDTO result = service.confirmDraft(10L, 20L, 1L);

        assertEquals(1L, result.getId());
        verify(orderService, times(1)).createInvestmentDraftOrder(
                10L, 20L, 5L, "BUY", new BigDecimal("1000"), 7L, null, null, "买入纳指ETF");
        verify(mapper).markConfirmed(1L, null, "ORD-BUY-1");
        verifyNoInteractions(quickEntryService);
    }

    @Test
    void confirmSubscriptionDraftCreatesOrderWithoutSettlement() {
        DraftLedgerEntry draft = bindDraft("{\"txnType\":\"SUBSCRIPTION\",\"productId\":6,\"accountId\":7,\"amount\":500}");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);
        when(mapper.selectVisibleById(1L, 10L, 20L)).thenReturn(draft);
        when(productMasterMapper.selectById(6L)).thenReturn(activeProduct(6L, "FUND", "CNY"));
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(investableAccount(7L, "基金账户", "CNY"));
        when(orderService.createInvestmentDraftOrder(eq(10L), eq(20L), eq(6L), eq("SUBSCRIPTION"),
                eq(new BigDecimal("500")), eq(7L), any(), any(), any())).thenReturn(pendingOrder("ORD-SUB-1"));
        when(mapper.markConfirmed(1L, null, "ORD-SUB-1")).thenReturn(1);

        service.confirmDraft(10L, 20L, 1L);

        verify(orderService).createInvestmentDraftOrder(eq(10L), eq(20L), eq(6L), eq("SUBSCRIPTION"),
                eq(new BigDecimal("500")), eq(7L), any(), any(), any());
        verify(mapper).markConfirmed(1L, null, "ORD-SUB-1");
        verify(quickEntryService, never()).quickTransfer(any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmingAlreadyConfirmedDraftDoesNotCreateSecondOrder() {
        DraftLedgerEntry draft = new DraftLedgerEntry();
        draft.setId(1L);
        draft.setOwnerUserId(10L);
        draft.setOwnerFamilyId(20L);
        draft.setStatus("CONFIRMED");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);

        DraftLedgerEntryDTO result = service.confirmDraft(10L, 20L, 1L);

        assertEquals(1L, result.getId());
        verifyNoInteractions(orderService);
        verifyNoInteractions(quickEntryService);
        verify(mapper, never()).markConfirmed(any(), any(), any());
    }

    @Test
    void unsupportedInvestmentPreviewRejectsConfirmWithoutOrder() {
        DraftLedgerEntry draft = bindDraft("{\"txnType\":\"BUY\",\"accountId\":7,\"amount\":1000}");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(investableAccount(7L, "证券投资账户", "CNY"));

        assertThrows(RuntimeException.class, () -> service.confirmDraft(10L, 20L, 1L));

        verifyNoInteractions(orderService);
        verify(mapper, never()).markConfirmed(any(), any(), any());
    }

    @Test
    void failingMarkConfirmedRollsBackDraftToDraft() {
        DraftLedgerEntry draft = bindDraft("{\"txnType\":\"BUY\",\"productId\":5,\"accountId\":7,\"amount\":1000}");
        when(mapper.selectVisibleByIdForUpdate(1L, 10L, 20L)).thenReturn(draft);
        when(productMasterMapper.selectById(5L)).thenReturn(activeProduct(5L, "ETF", "CNY"));
        when(accountMapper.selectVisibleRealById(7L, 10L, 20L)).thenReturn(investableAccount(7L, "证券投资账户", "CNY"));
        when(orderService.createInvestmentDraftOrder(eq(10L), eq(20L), eq(5L), eq("BUY"),
                eq(new BigDecimal("1000")), eq(7L), any(), any(), any())).thenReturn(pendingOrder("ORD-BUY-2"));
        when(mapper.markConfirmed(1L, null, "ORD-BUY-2")).thenReturn(0);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.confirmDraft(10L, 20L, 1L));

        assertTrue(error.getMessage().contains("回滚"));
        assertEquals("DRAFT", draft.getStatus());
        assertNull(draft.getConfirmOrderId());
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

    private Account investableAccount(Long id, String name, String currency) {
        Account account = realAccount(id, name, "INVESTABLE", currency);
        account.setBalance(new BigDecimal("5000"));
        account.setReservedAmount(BigDecimal.ZERO);
        return account;
    }

    private Account realAccount(Long id, String name, String fundUsage, String currency) {
        Account account = new Account();
        account.setId(id);
        account.setAccountName(name);
        account.setAccountKind("REAL");
        account.setAccountType("BROKER");
        account.setFundUsage(fundUsage);
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