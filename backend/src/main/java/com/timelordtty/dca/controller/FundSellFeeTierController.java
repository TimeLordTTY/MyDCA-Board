package com.timelordtty.dca.controller;

import com.timelordtty.dca.mapper.FundSellFeeTierMapper;
import com.timelordtty.dca.model.FundSellFeeTier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 场外基金卖出费率分段控制器
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v2/products/{productId}/sell-fee-tiers")
/**
 * 业务注释规范化: FundSellFeeTierController 控制器，负责接收前端请求、读取用户上下文，并将业务处理委托给服务层。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class FundSellFeeTierController {

    /**
     * 业务注释规范化: fundSellFeeTierMapper 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private final FundSellFeeTierMapper fundSellFeeTierMapper;

    /**
     * 业务注释规范化: 处理 FundSellFeeTierController 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param fundSellFeeTierMapper fundSellFeeTierMapper 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    public FundSellFeeTierController(FundSellFeeTierMapper fundSellFeeTierMapper) {
        this.fundSellFeeTierMapper = fundSellFeeTierMapper;
    }

    /**
     * 获取产品的所有费率分段
     * 
     * @param productId 产品ID
     * @return 费率分段列表
     */
    @GetMapping
    /**
     * 业务注释规范化: 查询 getFeeTiers 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<List<FundSellFeeTier>> getFeeTiers(@PathVariable Long productId) {
        List<FundSellFeeTier> tiers = fundSellFeeTierMapper.selectByProductId(productId);
        return ResponseEntity.ok(tiers);
    }

    /**
     * 保存产品的费率分段配置（先删除旧的，再插入新的）
     * 
     * @param productId 产品ID
     * @param tiers 费率分段列表
     * @return 保存结果
     */
    @PostMapping
    /**
     * 业务注释规范化: 保存 saveFeeTiers 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @param tiers tiers 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<Map<String, Object>> saveFeeTiers(
            @PathVariable Long productId,
            @RequestBody List<FundSellFeeTier> tiers) {
        // 删除旧的费率分段
        fundSellFeeTierMapper.deleteByProductId(productId);
        
        // 插入新的费率分段
        int count = 0;
        for (FundSellFeeTier tier : tiers) {
            tier.setProductId(productId);
            if (tier.getIsActive() == null) {
                tier.setIsActive(true);
            }
            fundSellFeeTierMapper.insert(tier);
            count++;
        }
        
        return ResponseEntity.ok(Map.of("productId", productId, "savedCount", count));
    }

    /**
     * 删除产品的所有费率分段
     * 
     * @param productId 产品ID
     * @return 删除结果
     */
    @DeleteMapping
    /**
     * 业务注释规范化: 删除 deleteFeeTiers 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public ResponseEntity<Map<String, Object>> deleteFeeTiers(@PathVariable Long productId) {
        int count = fundSellFeeTierMapper.deleteByProductId(productId);
        return ResponseEntity.ok(Map.of("productId", productId, "deletedCount", count));
    }
}
