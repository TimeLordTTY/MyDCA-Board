package com.timelordtty.dca.controller;

import com.timelordtty.dca.model.MarketBarDaily;
import com.timelordtty.dca.model.MarketQuoteRealtime;
import com.timelordtty.dca.service.MarketService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 行情控制器
 */
@RestController
@RequestMapping("/api/v2/market")
/**
 * 业务注释规范化: MarketController 控制器，负责接收前端请求、读取用户上下文，并将业务处理委托给服务层。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class MarketController {

    /**
     * 业务注释规范化: marketService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final MarketService marketService;

    /**
     * 业务注释规范化: 处理 MarketController 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param marketService marketService 业务字段，承载该对象在后端流程中的核心属性。
     */
    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    /**
     * 获取历史行情（日K线）
     * GET /api/v2/market/bars?productId=1&startDate=2024-01-01&endDate=2024-12-31
     */
    @GetMapping("/bars")
    /**
     * 业务注释规范化: 查询 getHistoryBars 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 查询 getLatestBar 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 查询 getRealtimeQuotes 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productIds productIds 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<List<MarketQuoteRealtime>> getRealtimeQuotes(@RequestParam List<Long> productIds) {
        List<MarketQuoteRealtime> quotes = marketService.getRealtimeQuotes(productIds);
        return ResponseEntity.ok(quotes);
    }

    /**
     * 获取单个产品的最新实时行情
     * GET /api/v2/market/quotes/latest?productId=1
     */
    @GetMapping("/quotes/latest")
    /**
     * 业务注释规范化: 查询 getLatestQuote 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<MarketQuoteRealtime> getLatestQuote(@RequestParam Long productId) {
        MarketQuoteRealtime quote = marketService.getLatestQuote(productId);
        if (quote == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(quote);
    }

    /**
     * 获取实时行情历史（用于IOPV/估值曲线）
     * GET /api/v2/market/quotes/history?productId=1&startTime=2026-01-01T09:30:00&endTime=2026-01-01T15:00:00
     */
    @GetMapping("/quotes/history")
    /**
     * 业务注释规范化: 查询 getQuoteHistory 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<List<MarketQuoteRealtime>> getQuoteHistory(
            @RequestParam Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        if (startTime == null) {
            startTime = endTime.minusDays(7);
        }
        return ResponseEntity.ok(marketService.getQuoteHistory(productId, startTime, endTime));
    }
}
