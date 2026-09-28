package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.SettlementPreviewDTO;
import com.timelordtty.dca.dto.SettlementPreviewRequest;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.model.SettlementConfirm;
import com.timelordtty.dca.service.OrderService;
import com.timelordtty.dca.service.SettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 结算控制器
 *
 * <p>v0.13.0 起，人工结算的客户端必须走「只读 preview -> 主人二次确认 -> 携带 fresh preview 令牌 confirm」链路。
 * preview 不产生任何业务写入；confirm 才会真正生成 settlement_confirm 与内部账本，并且必须携带 preview 返回的令牌。</p>
 */
@RestController
@RequestMapping("/api/v2/settlements")
public class SettlementController {

    /**
     * 订单服务入口，负责订单查询、创建、取消以及与结算流程的衔接。
     */
    private final OrderService orderService;
    /**
     * 结算服务入口，负责订单成交确认、费用拆分和账本落账编排。
     */
    private final SettlementService settlementService;

    /**
     * 装配订单与结算服务，处理待结算订单查询和成交确认入口。
     */
    public SettlementController(OrderService orderService, SettlementService settlementService) {
        this.orderService = orderService;
        this.settlementService = settlementService;
    }

    /**
     * 查询待结算订单列表，供首页或结算页提醒需要确认成交的订单。
     */
    @GetMapping("/pending")
    public ResponseEntity<List<Order>> getPendingSettlements() {
        List<Order> orders = orderService.getPendingOrders();
        return ResponseEntity.ok(orders);
    }

    /**
     * 人工结算只读预览。
     *
     * <p>校验订单状态与归属、产品、资金来源行与输入参数，并返回现金 / 持仓 / 手续费影响与 fresh preview 令牌。
     * 该接口不会写 settlement_confirm、ledger_txn / ledger_posting，也不会改 reserved_amount、
     * initial_shares 或 order.status。</p>
     */
    @PostMapping("/preview")
    public ResponseEntity<SettlementPreviewDTO> previewSettlement(@RequestBody SettlementPreviewRequest request) {
        SettlementPreviewDTO preview = settlementService.previewSettlement(
                request.getOrderId(),
                parseDate(request.getConfirmDate()),
                parseDate(request.getNavDate()),
                request.getConfirmNav(),
                request.getConfirmShares(),
                request.getConfirmAmount(),
                request.getConfirmFee());
        return ResponseEntity.ok(preview);
    }

    /**
     * 确认订单成交结算，写入结算明细并交由服务层生成账本影响。
     *
     * <p>必须携带同一组结算输入与 freshPreviewToken；服务端会重新计算并比对指纹，任何关键输入、
     * 订单、资金来源或账户快照变化都会阻断本次确认。</p>
     */
    @PostMapping("/confirm")
    public ResponseEntity<SettlementConfirm> confirmSettlement(@RequestBody SettlementPreviewRequest request) {
        SettlementConfirm settlement = settlementService.confirmSettlement(
                request.getOrderId(),
                parseDate(request.getConfirmDate()),
                parseDate(request.getNavDate()),
                request.getConfirmNav(),
                request.getConfirmShares(),
                request.getConfirmAmount(),
                request.getConfirmFee(),
                request.getFreshPreviewToken());
        return ResponseEntity.ok(settlement);
    }

    /** 宽松解析 ISO 日期；非法 / 空值返回 null，由服务层作为阻断原因返回。 */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 10) {
            normalized = normalized.substring(0, 10);
        }
        try {
            return LocalDate.parse(normalized);
        } catch (Exception e) {
            return null;
        }
    }
}