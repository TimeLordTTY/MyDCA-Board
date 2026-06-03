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
public class NavController {

    /**
     * 依赖的 NavService 服务，用于复用该领域的业务校验和事务逻辑。
     */
    private final NavService navService;

    /**
     * 处理写入类 API，将请求参数校验后委托给 Service 层。
     * 是否产生账本、账户或订单变更由对应 Service 事务边界决定。
     */
    public NavController(NavService navService) {
        this.navService = navService;
    }

    /**
     * 获取历史净值
     * GET /api/v2/nav/history?productId=1&startDate=2024-01-01&endDate=2024-12-31
     */
    @GetMapping("/history")
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
