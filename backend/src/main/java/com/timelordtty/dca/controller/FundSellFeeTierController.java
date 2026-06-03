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
public class FundSellFeeTierController {

    private final FundSellFeeTierMapper fundSellFeeTierMapper;

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
    public ResponseEntity<Map<String, Object>> deleteFeeTiers(@PathVariable Long productId) {
        int count = fundSellFeeTierMapper.deleteByProductId(productId);
        return ResponseEntity.ok(Map.of("productId", productId, "deletedCount", count));
    }
}
