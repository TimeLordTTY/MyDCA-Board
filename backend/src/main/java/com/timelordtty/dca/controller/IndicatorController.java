package com.timelordtty.dca.controller;

import com.timelordtty.dca.model.IndicatorDaily;
import com.timelordtty.dca.service.IndicatorService;
import com.timelordtty.dca.service.PythonScriptService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标控制器
 */
@RestController
@RequestMapping("/api/v2/indicators")
public class IndicatorController {

    private final IndicatorService indicatorService;
    private final PythonScriptService pythonScriptService;

    public IndicatorController(IndicatorService indicatorService, PythonScriptService pythonScriptService) {
        this.indicatorService = indicatorService;
        this.pythonScriptService = pythonScriptService;
    }

    /**
     * 获取历史指标数据
     * GET /api/v2/indicators/history?productId=1&startDate=2024-01-01&endDate=2024-12-31&windowDays=20
     */
    @GetMapping("/history")
    public ResponseEntity<List<IndicatorDaily>> getHistoryIndicators(
            @RequestParam Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "20") Integer windowDays) {
        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(3); // 默认3个月
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        List<IndicatorDaily> indicators = indicatorService.getHistoryIndicators(productId, startDate, endDate, windowDays);
        return ResponseEntity.ok(indicators);
    }

    /**
     * 获取最新指标数据
     * GET /api/v2/indicators/latest?productId=1&windowDays=20
     */
    @GetMapping("/latest")
    public ResponseEntity<IndicatorDaily> getLatestIndicator(
            @RequestParam Long productId,
            @RequestParam(required = false, defaultValue = "20") Integer windowDays) {
        IndicatorDaily indicator = indicatorService.getLatestIndicator(productId, windowDays);
        if (indicator == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(indicator);
    }

    /**
     * 手动触发指标计算（可选：指定产品与日期）
     * POST /api/v2/indicators/calculate?productId=1&endDate=2026-03-10
     */
    @PostMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculateIndicators(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String result = pythonScriptService.runIndicatorCalculator(productId, endDate);
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", !result.startsWith("失败:"));
        resp.put("productId", productId);
        resp.put("endDate", endDate);
        resp.put("message", result);
        return ResponseEntity.ok(resp);
    }
}
