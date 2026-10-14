package com.timelordtty.dca.service;

import com.timelordtty.dca.controller.OrderController;
import com.timelordtty.dca.controller.SettlementController;
import com.timelordtty.dca.exception.GlobalExceptionHandler;
import com.timelordtty.dca.mapper.*;
import com.timelordtty.dca.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Offline HTTP -> real service -> mocked repository boundary; no application/DB/server is started. */
class OrderScopeSecurityTest {
    private final OrderMapper orders = mock(OrderMapper.class);
    private final AccountMapper accounts = mock(AccountMapper.class);
    private final OrderFundingLineMapper funding = mock(OrderFundingLineMapper.class);
    private final SettlementConfirmMapper settlements = mock(SettlementConfirmMapper.class);
    private final LedgerTxnMapper txns = mock(LedgerTxnMapper.class);
    private final LedgerPostingMapper postings = mock(LedgerPostingMapper.class);
    private final ProductMasterMapper products = mock(ProductMasterMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final AccountService accountService = mock(AccountService.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final BrokerFeeService fees = mock(BrokerFeeService.class);
    private final UserService identities = new UserService(users, mock(PasswordEncoder.class));
    private final OrderService orderService = new OrderService(orders, accounts, accountService, funding,
            settlements, txns, postings, ledger, identities, products);
    private final SettlementService settlementService = new SettlementService(orders, settlements, accounts,
            ledger, funding, identities, accountService, products, fees);
    private final SettlementAuditService auditService = new SettlementAuditService(orders, settlements,
            funding, txns, postings, products, accounts, identities);
    private MockMvc http;
    private static final String INPUT = "{\"orderId\":\"OWNER-A\",\"confirmDate\":\"2026-10-13\","
            + "\"navDate\":\"2026-10-13\",\"confirmNav\":2,\"confirmFee\":0,"
            + "\"freshPreviewToken\":\"stolen-preview\",\"userId\":1,\"familyId\":10}";

    @BeforeEach void setup() {
        http = MockMvcBuilders.standaloneSetup(new OrderController(orderService, identities, settlementService,
                        txns, postings, fees, products),
                new SettlementController(orderService, settlementService, auditService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(orders.selectByOrderId("OWNER-A")).thenReturn(order("OWNER-A", 1L, "PENDING"));
        when(orders.selectByOrderId("OWNER-B")).thenReturn(order("OWNER-B", 2L, "PENDING"));
        when(orders.selectByStatus("PENDING")).thenReturn(List.of(
                order("OWNER-A", 1L, "PENDING"), order("OWNER-B", 2L, "PENDING")));
        when(orders.selectByUserId(1L)).thenReturn(List.of(order("OWNER-A", 1L, "PENDING")));
        when(orders.selectByUserId(2L)).thenReturn(List.of(order("OWNER-B", 2L, "PENDING")));
        signIn(1L, 10L, "ADMIN");
    }

    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test void statusAndPendingQueriesAreScopedBeforeReturningRows() throws Exception {
        http.perform(get("/api/v2/orders").param("status", "PENDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(1));
        http.perform(get("/api/v2/settlements/pending"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        verify(orders, never()).selectByStatus(anyString());
    }

    @Test void defaultListAndExplicitUserArgumentCannotChangeIdentity() throws Exception {
        http.perform(get("/api/v2/orders").param("userId", "2").param("familyId", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].userId").value(1));
        assertThrows(RuntimeException.class, () -> orderService.getOrdersByUserId(2L));
        verify(orders, never()).selectByUserId(2L);
    }

    @Test void statusFilterPreservesPendingDateOrderAndExcludesFinalRows() throws Exception {
        Order later = order("LATER", 1L, "PENDING");
        later.setExpectedConfirmDate(LocalDate.of(2026, 10, 15));
        Order undated = order("UNDATED", 1L, "PENDING");
        undated.setExpectedConfirmDate(null);
        when(orders.selectByUserId(1L)).thenReturn(List.of(later,
                order("FINAL", 1L, "CONFIRMED"), order("OWNER-A", 1L, "PENDING"), undated));
        http.perform(get("/api/v2/orders").param("status", "PENDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].orderId").value("UNDATED"))
                .andExpect(jsonPath("$[1].orderId").value("OWNER-A"))
                .andExpect(jsonPath("$[2].orderId").value("LATER"));
        verify(orders, never()).selectByStatus(anyString());
    }

    @Test void foreignCancelIsRejectedBeforeAnyRelatedReadOrWrite() throws Exception {
        signIn(2L, 20L, "ADMIN");
        http.perform(post("/api/v2/orders/OWNER-A/cancel")).andExpect(status().isBadRequest());
        verifyNoInteractions(txns, postings, funding, accounts, ledger);
        verify(orders, never()).update(any());
    }

    @Test void foreignPreviewDoesNotLeakEvenFinalOrderMetadata() throws Exception {
        signIn(2L, 20L, "MEMBER");
        when(orders.selectByOrderId("OWNER-A")).thenReturn(order("OWNER-A", 1L, "CONFIRMED"));
        http.perform(post("/api/v2/settlements/preview").contentType("application/json").content(INPUT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmSupported").value(false))
                .andExpect(jsonPath("$.productId").isEmpty()).andExpect(jsonPath("$.orderStatus").isEmpty());
        verifyNoInteractions(funding, settlements, products, accounts);
    }

    @ParameterizedTest
    @CsvSource({"20,ADMIN", "20,MEMBER", "10,ADMIN", "10,MEMBER", "20,VIEWER"})
    void foreignOwnerDeniedEvenForSameFamilyOrAdmin(Long family, String role) throws Exception {
        signIn(2L, family, role);
        http.perform(get("/api/v2/orders/OWNER-A"))
                .andExpect(status().isNotFound());
        http.perform(post("/api/v2/orders/OWNER-A/cancel"))
                .andExpect(status().isBadRequest());
        http.perform(post("/api/v2/settlements/preview").contentType("application/json").content(INPUT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmSupported").value(false))
                .andExpect(jsonPath("$.productId").isEmpty()).andExpect(jsonPath("$.orderStatus").isEmpty())
                .andExpect(jsonPath("$.orderType").isEmpty()).andExpect(jsonPath("$.freshPreviewToken").isEmpty());
        http.perform(post("/api/v2/settlements/confirm").contentType("application/json").content(INPUT))
                .andExpect(status().isBadRequest());
        http.perform(post("/api/v2/orders/OWNER-A/settle").contentType("application/json").content(INPUT))
                .andExpect(status().isBadRequest());
        http.perform(get("/api/v2/settlements/history/OWNER-A"))
                .andExpect(status().isNotFound());
        assertThrows(RuntimeException.class, () -> orderService.getOrderFundingLines("OWNER-A"));
        assertThrows(RuntimeException.class, () -> orderService.getSettlementByOrderId("OWNER-A"));
        verifyNoInteractions(funding, settlements, txns, postings, accounts, products, ledger, accountService);
        verify(orders, never()).update(any());
    }

    @Test void missingAndOwnerlessIdsDoNotExposeRelatedDataOrPermitWrites() throws Exception {
        when(orders.selectByOrderId("NO-OWNER")).thenReturn(order("NO-OWNER", null, "PENDING"));
        for (String id : List.of("RANDOM-UNKNOWN", "NO-OWNER", " ")) {
            assertNull(orderService.getOrderByOrderId(id));
            assertThrows(RuntimeException.class, () -> orderService.cancelOrder(id));
            var preview = settlementService.previewSettlement(id, null, null, null, null, null, null);
            assertFalse(preview.isConfirmSupported());
            assertNull(preview.getOrderType());
            assertThrows(RuntimeException.class, () -> settlementService.confirmSettlement(
                    id, null, null, null, null, null, null, "stolen-preview"));
        }
        verifyNoInteractions(funding, settlements, txns, postings, accounts, products, ledger);
    }

    @Test void sameServiceInstanceRechecksIdentityForListsDetailsAndIdempotentConfirm() throws Exception {
        var existing = new SettlementConfirm();
        existing.setOrderId("OWNER-A");
        when(settlements.selectByOrderId("OWNER-A")).thenReturn(existing);
        assertSame(existing, settlementService.confirmSettlement("OWNER-A", null, null, null,
                null, null, null, "old-token"));
        http.perform(get("/api/v2/orders/OWNER-A")).andExpect(status().isOk());
        clearInvocations(settlements, funding, txns, postings, products);
        signIn(2L, 20L, "ADMIN");
        assertNull(orderService.getOrderByOrderId("OWNER-A"));
        assertEquals("OWNER-B", orderService.getPendingOrders().get(0).getOrderId());
        assertThrows(RuntimeException.class, () -> settlementService.confirmSettlement("OWNER-A", null,
                null, null, null, null, null, "old-token"));
        assertNull(auditService.detail("OWNER-A"));
        verify(settlements, never()).selectByOrderId("OWNER-A");
        verifyNoInteractions(ledger, accountService);
    }

    @ParameterizedTest @CsvSource({"ADMIN", "MEMBER"})
    void ownerCanReadAndCancelPendingButCannotCancelFinalStates(String role) throws Exception {
        signIn(1L, 10L, role);
        http.perform(get("/api/v2/orders/OWNER-A")).andExpect(status().isOk());
        http.perform(post("/api/v2/orders/OWNER-A/cancel")).andExpect(status().isOk());
        verify(orders).update(argThat(o -> "CANCELLED".equals(o.getStatus())));
        for (String state : List.of("CANCELLED", "CONFIRMED", "FAILED")) {
            when(orders.selectByOrderId("FINAL")).thenReturn(order("FINAL", 1L, state));
            assertThrows(RuntimeException.class, () -> orderService.cancelOrder("FINAL"));
        }
        verifyNoInteractions(ledger);
    }

    @Test void historyUsesOwnerQueryAndRejectsForeignDetails() throws Exception {
        var existing = new SettlementConfirm();
        existing.setOrderId("OWNER-A");
        when(settlements.selectByOrderId("OWNER-A")).thenReturn(existing);
        http.perform(get("/api/v2/settlements/history"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value("OWNER-A"));
        http.perform(get("/api/v2/settlements/history/OWNER-A")).andExpect(status().isOk());
        clearInvocations(settlements, funding, txns, postings, products, accounts);
        assertNull(auditService.detail("OWNER-B"));
        verifyNoInteractions(settlements, funding, txns, postings, products, accounts);
    }

    @Test void concurrentForeignCancelAndConfirmRemainDenied() throws Exception {
        signIn(2L, 20L, "ADMIN");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var cancel = CompletableFuture.runAsync(() -> deniedConcurrentAction(ready, start, true));
        var confirm = CompletableFuture.runAsync(() -> deniedConcurrentAction(ready, start, false));
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        cancel.get(5, TimeUnit.SECONDS);
        confirm.get(5, TimeUnit.SECONDS);
        verifyNoInteractions(settlements, funding, accounts, txns, postings, ledger, accountService);
        verify(orders, never()).update(any());
    }

    private void deniedConcurrentAction(CountDownLatch ready, CountDownLatch start, boolean cancel) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "offline-2", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            assertThrows(RuntimeException.class, () -> {
                if (cancel) orderService.cancelOrder("OWNER-A");
                else settlementService.confirmSettlement("OWNER-A", null, null, null,
                        null, null, null, "stolen-preview");
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void signIn(Long id, Long family, String role) {
        String name = "offline-" + id;
        User user = new User();
        user.setId(id); user.setUsername(name); user.setFamilyId(family);
        when(users.selectByUsername(name)).thenReturn(user);
        // Family roles are not a grant to other owners' orders; authority labels exercise that boundary.
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                name, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static Order order(String id, Long owner, String state) {
        Order row = new Order();
        row.setId("OWNER-B".equals(id) ? 2L : 1L); row.setOrderId(id); row.setUserId(owner);
        row.setProductId(101L); row.setOrderType("BUY"); row.setStatus(state);
        row.setExpectedConfirmDate(LocalDate.of(2026, 10, 13)); row.setAmount(BigDecimal.TEN);
        return row;
    }
}
