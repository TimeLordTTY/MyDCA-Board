package com.timelordtty.dca.controller;

import com.timelordtty.dca.model.MarketBarDaily;
import com.timelordtty.dca.model.MarketQuoteRealtime;
import com.timelordtty.dca.service.MarketService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 行情控制器
 */
@RestController
@RequestMapping("/api/v2/market")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    /**
     * 获取历史行情（日K线）
     * GET /api/v2/market/bars?productId=1&startDate=2024-01-01&endDate=2024-12-31
     */
    @GetMapping("/bars")
    public ResponseEntity<List<MarketBarDaily>> getHistoryBars(
            @RequestParam Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(3); // 默认3个月
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        List<MarketBarDaily> bars = marketService.getHistoryBars(productId, startDate, endDate);
        return ResponseEntity.ok(bars);
    }

    /**
     * 获取最新日K线
     * GET /api/v2/market/bars/latest?productId=1
     */
    @GetMapping("/bars/latest")
    public ResponseEntity<MarketBarDaily> getLatestBar(@RequestParam Long productId) {
        MarketBarDaily bar = marketService.getLatestBar(productId);
        if (bar == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bar);
    }

    /**
     * 获取实时行情（批量）
     * GET /api/v2/market/quotes?productIds=1,2,3
     */
    @GetMapping("/quotes")
    public ResponseEntity<List<MarketQuoteRealtime>> getRealtimeQuotes(@RequestParam List<Long> productIds) {
        List<MarketQuoteRealtime> quotes = marketService.getRealtimeQuotes(productIds);
        return ResponseEntity.ok(quotes);
    }

    /**
     * 获取单个产品的最新实时行情
     * GET /api/v2/market/quotes/latest?productId=1
     */
    @GetMapping("/quotes/latest")
    public ResponseEntity<MarketQuoteRealtime> getLatestQuote(@RequestParam Long productId) {
        MarketQuoteRealtime quote = marketService.getLatestQuote(productId);
        if (quote == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(quote);
    }
}
