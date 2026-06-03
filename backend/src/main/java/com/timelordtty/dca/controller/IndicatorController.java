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
/**
 * 业务注释规范化: IndicatorController 控制器，负责接收前端请求、读取用户上下文，并将业务处理委托给服务层。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class IndicatorController {

    /**
     * 业务注释规范化: indicatorService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final IndicatorService indicatorService;
    /**
     * 业务注释规范化: pythonScriptService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final PythonScriptService pythonScriptService;

    /**
     * 业务注释规范化: 处理 IndicatorController 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param indicatorService indicatorService 业务字段，承载该对象在后端流程中的核心属性。
     * @param pythonScriptService pythonScriptService 业务字段，承载该对象在后端流程中的核心属性。
     */
    public IndicatorController(IndicatorService indicatorService, PythonScriptService pythonScriptService) {
        this.indicatorService = indicatorService;
        this.pythonScriptService = pythonScriptService;
    }

    /**
     * 获取历史指标数据
     * GET /api/v2/indicators/history?productId=1&startDate=2024-01-01&endDate=2024-12-31&windowDays=20
     */
    @GetMapping("/history")
    /**
     * 业务注释规范化: 查询 getHistoryIndicators 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 查询 getLatestIndicator 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @param false false 业务字段，承载该对象在后端流程中的核心属性。
     * @param windowDays windowDays 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
    /**
     * 业务注释规范化: 计算 calculateIndicators 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param false false 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
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
