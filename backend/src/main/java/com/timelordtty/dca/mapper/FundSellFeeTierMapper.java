package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.FundSellFeeTier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 场外基金卖出费率分段Mapper接口
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Mapper
public interface FundSellFeeTierMapper {
    
    /**
     * 根据ID查询费率分段
     * 
     * @param id 费率分段ID
     * @return 费率分段对象
     */
    FundSellFeeTier selectById(@Param("id") Long id);
    
    /**
     * 根据产品ID查询所有费率分段（按sort_order排序）
     * 
     * @param productId 产品ID
     * @return 费率分段列表
     */
    List<FundSellFeeTier> selectByProductId(@Param("productId") Long productId);
    
    /**
     * 根据产品ID和持有天数查询对应的费率分段
     * 
     * @param productId 产品ID
     * @param holdingDays 持有天数
     * @return 费率分段对象，如果不存在则返回null
     */
    FundSellFeeTier selectByProductIdAndHoldingDays(@Param("productId") Long productId, 
                                                      @Param("holdingDays") Integer holdingDays);
    
    /**
     * 插入费率分段
     * 
     * @param tier 费率分段对象
     * @return 影响行数
     */
    int insert(FundSellFeeTier tier);
    
    /**
     * 更新费率分段
     * 
     * @param tier 费率分段对象
     * @return 影响行数
     */
    int update(FundSellFeeTier tier);
    
    /**
     * 删除费率分段
     * 
     * @param id 费率分段ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 根据产品ID删除所有费率分段
     * 
     * @param productId 产品ID
     * @return 影响行数
     */
    int deleteByProductId(@Param("productId") Long productId);
}
