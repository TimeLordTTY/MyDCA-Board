package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.OrderFundingLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
