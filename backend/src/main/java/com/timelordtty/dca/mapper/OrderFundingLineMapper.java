package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.OrderFundingLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单资金来源拆分表Mapper接口
 * 
 * 对应数据库表：order_funding_line
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Mapper
public interface OrderFundingLineMapper {
    
    /**
     * 根据订单ID查询所有资金来源行
     * 
     * @param orderId 订单ID
     * @return 资金来源行列表
     */
    List<OrderFundingLine> selectByOrderId(@Param("orderId") String orderId);
    
    /**
     * 插入资金来源行
     * 
     * @param fundingLine 资金来源行实体
     * @return 影响行数
     */
    int insert(OrderFundingLine fundingLine);
    
    /**
     * 批量插入资金来源行
     * 
     * @param fundingLines 资金来源行列表
     * @return 影响行数
     */
    int batchInsert(@Param("fundingLines") List<OrderFundingLine> fundingLines);
    
    /**
     * 根据订单ID删除所有资金来源行（用于取消订单）
     * 
     * @param orderId 订单ID
     * @return 影响行数
     */
    int deleteByOrderId(@Param("orderId") String orderId);

    /**
     * 统计指定产品 + 持仓来源账户下仍为 PENDING 的 SELL / REDEMPTION 占用份额。
     *
     * <p>SELL / REDEMPTION 下单阶段只锁定份额，不生成账本，因此可用份额必须是
     * “真实持仓份额 - 同产品/来源账户下 PENDING 卖出赎回占用份额”，避免系统内重复占用。</p>
     *
     * @param productId 产品 ID
     * @param userId 订单归属用户 ID
     * @param accountId 持仓来源账户 ID
     * @return 已占用份额合计；没有占用时返回 0
     */
    BigDecimal sumPendingSellSharesByAccount(@Param("productId") Long productId,
                                            @Param("userId") Long userId,
                                            @Param("accountId") Long accountId);
}
