package com.timelordtty.dca.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.AccountingIntentDTO;
import com.timelordtty.dca.dto.ParseTextRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * v0.11 文本候选解析：验证投资语义优先于 EXPENSE，且绝不按产品名称自动匹配真实 productId。
 */
class AiAccountingServiceInvestmentTest {

    private final DraftLedgerEntryService draftLedgerEntryService = mock(DraftLedgerEntryService.class);
    private final AiAccountingService service = new AiAccountingService(draftLedgerEntryService, new ObjectMapper());

    @Test
    void parseBuyTextProducesBuyCandidateWithProductHintAndMissingIds() {
        AccountingIntentDTO intent = service.parseText(text("买入沪深300ETF 1000"));

        assertEquals("BUY", intent.getTxnType());
        assertEquals(new BigDecimal("1000"), intent.getAmount());
        assertEquals("沪深300ETF", intent.getProductNameHint());
        assertNull(intent.getProductId());
        assertTrue(intent.getMissingFields().contains("productId"));
        assertTrue(intent.getMissingFields().contains("accountId"));
        verifyNoInteractions(draftLedgerEntryService);
    }

    @Test
    void parseSubscriptionTextProducesSubscriptionCandidate() {
        AccountingIntentDTO intent = service.parseText(text("申购兴全合润 500"));

        assertEquals("SUBSCRIPTION", intent.getTxnType());
        assertEquals(new BigDecimal("500"), intent.getAmount());
        assertEquals("兴全合润", intent.getProductNameHint());
        assertTrue(intent.getMissingFields().contains("productId"));
    }

    @Test
    void parseAutoInvestTextProducesSubscriptionCandidate() {
        AccountingIntentDTO intent = service.parseText(text("定投纳指 500"));

        assertEquals("SUBSCRIPTION", intent.getTxnType());
        assertEquals(new BigDecimal("500"), intent.getAmount());
        assertEquals("纳指", intent.getProductNameHint());
    }

    @Test
    void plainBuyingMilkTeaStaysExpense() {
        AccountingIntentDTO intent = service.parseText(text("买奶茶30"));

        assertEquals("EXPENSE", intent.getTxnType());
        assertEquals(new BigDecimal("30"), intent.getAmount());
        assertNull(intent.getProductNameHint());
        assertFalse(intent.getMissingFields().contains("productId"));
    }

    @Test
    void payingForLunchStaysExpense() {
        AccountingIntentDTO intent = service.parseText(text("支付午饭 30"));

        assertEquals("EXPENSE", intent.getTxnType());
        assertEquals(new BigDecimal("30"), intent.getAmount());
        assertFalse(intent.getMissingFields().contains("productId"));
    }

    @Test
    void investmentIntentJsonNeverContainsAutoMatchedProductId() {
        AccountingIntentDTO intent = service.parseText(text("买入纳指ETF 1000"));

        assertTrue(intent.getParsedPayloadJson().contains("\"productNameHint\":\"纳指ETF\""));
        assertTrue(intent.getParsedPayloadJson().contains("\"productId\":null"));
    }

    @Test
    void parseSellTextProducesSellCandidateWithSharesAndProductHint() {
        AccountingIntentDTO intent = service.parseText(text("卖出半导体ETF 500份"));

        assertEquals("SELL", intent.getTxnType());
        assertEquals(new BigDecimal("500"), intent.getShares());
        assertEquals("半导体ETF", intent.getProductNameHint());
        assertNull(intent.getAmount());
        assertNull(intent.getProductId());
        assertTrue(intent.getMissingFields().contains("productId"));
        assertTrue(intent.getMissingFields().contains("sourceAccountId"));
        assertTrue(intent.getMissingFields().contains("targetAccountId"));
        assertFalse(intent.getMissingFields().contains("amount"));
        assertEquals(new BigDecimal("0.70"), intent.getConfidence());
        verifyNoInteractions(draftLedgerEntryService);
    }

    @Test
    void parseRedeemTextProducesRedemptionCandidateWithSharesAndProductHint() {
        AccountingIntentDTO intent = service.parseText(text("赎回兴全合润 1000份"));

        assertEquals("REDEMPTION", intent.getTxnType());
        assertEquals(new BigDecimal("1000"), intent.getShares());
        assertEquals("兴全合润", intent.getProductNameHint());
        assertNull(intent.getAmount());
        assertTrue(intent.getMissingFields().contains("productId"));
        assertTrue(intent.getMissingFields().contains("sourceAccountId"));
        assertTrue(intent.getMissingFields().contains("targetAccountId"));
    }

    @Test
    void sellTextWithoutSharesReportsMissingShares() {
        AccountingIntentDTO intent = service.parseText(text("卖出半导体ETF"));

        assertEquals("SELL", intent.getTxnType());
        assertNull(intent.getShares());
        assertTrue(intent.getMissingFields().contains("shares"));
    }

    @Test
    void sellIntentJsonNeverContainsAutoMatchedProductOrAccounts() {
        AccountingIntentDTO intent = service.parseText(text("卖出纳指ETF 500份"));

        assertTrue(intent.getParsedPayloadJson().contains("\"shares\":500"));
        assertTrue(intent.getParsedPayloadJson().contains("\"productNameHint\":\"纳指ETF\""));
        assertTrue(intent.getParsedPayloadJson().contains("\"productId\":null"));
        assertTrue(intent.getParsedPayloadJson().contains("\"sourceAccountId\":null"));
        assertTrue(intent.getParsedPayloadJson().contains("\"targetAccountId\":null"));
    }

    private ParseTextRequest text(String value) {
        ParseTextRequest request = new ParseTextRequest();
        request.setText(value);
        return request;
    }
}