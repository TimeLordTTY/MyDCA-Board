package com.timelordtty.dca.controller;

import com.timelordtty.dca.model.Nav;
import com.timelordtty.dca.service.NavService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 净值控制器
 */
@RestController
@RequestMapping("/api/v2/nav")
/**
 * 业务注释规范化: NavController 控制器，负责接收前端请求、读取用户上下文，并将业务处理委托给服务层。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class NavController {

    /**
     * 业务注释规范化: navService 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final NavService navService;

    /**
     * 业务注释规范化: 处理 NavController 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param navService navService 业务字段，承载该对象在后端流程中的核心属性。
     */
    public NavController(NavService navService) {
        this.navService = navService;
    }

    /**
     * 获取历史净值
     * GET /api/v2/nav/history?productId=1&startDate=2024-01-01&endDate=2024-12-31
     */
    @GetMapping("/history")
    /**
     * 业务注释规范化: 查询 getHistoryNav 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<List<Nav>> getHistoryNav(
            @RequestParam Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(3); // 默认3个月
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        List<Nav> navs = navService.getHistoryNav(productId, startDate, endDate);
        return ResponseEntity.ok(navs);
    }

    /**
     * 获取最新净值
     * GET /api/v2/nav/latest?productId=1
     */
    @GetMapping("/latest")
    /**
     * 业务注释规范化: 查询 getLatestNav 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<Nav> getLatestNav(@RequestParam Long productId) {
        Nav nav = navService.getLatestNav(productId);
        if (nav == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(nav);
    }

    /**
     * 获取指定日期的净值
     * GET /api/v2/nav/by-date?productId=1&navDate=2024-01-15
     */
    @GetMapping("/by-date")
    /**
     * 业务注释规范化: 查询 getNavByDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @param navDate navDate 日期字段，用于交易、确认、净值或统计周期口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<Nav> getNavByDate(
            @RequestParam Long productId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate navDate) {
        Nav nav = navService.getNavByDate(productId, navDate);
        if (nav == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(nav);
    }
}
