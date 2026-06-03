package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {
    Order selectById(@Param("id") Long id);
    Order selectByOrderId(@Param("orderId") String orderId);
    List<Order> selectByStatus(@Param("status") String status);
    List<Order> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 查询指定产品的已确认订单
     * @param productId 产品ID
     * @param userId 用户ID
     * @return 已确认订单列表
     */
    List<Order> selectConfirmedByProductId(@Param("productId") Long productId, @Param("userId") Long userId);
    
    int insert(Order order);
    int update(Order order);
}

