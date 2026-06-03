package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/**
 * 业务注释规范化: OrderMapper Mapper 接口，负责 MyBatis SQL 映射和持久化访问，真实业务规则由服务层保证。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public interface OrderMapper {
    /**
     * 业务注释规范化: 读取 selectById 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    Order selectById(@Param("id") Long id);
    /**
     * 业务注释规范化: 读取 selectByOrderId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param orderId 关联订单 ID，用于串联订单创建、资金冻结、结算确认和流水入账。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    Order selectByOrderId(@Param("orderId") String orderId);
    /**
     * 业务注释规范化: 读取 selectByStatus 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param status 业务状态，表示记录当前所处的创建、确认、取消或完成阶段。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<Order> selectByStatus(@Param("status") String status);
    /**
     * 业务注释规范化: 读取 selectByUserId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<Order> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 查询指定产品的已确认订单
     * @param productId 产品ID
     * @param userId 用户ID
     * @return 已确认订单列表
     */
    /**
     * 业务注释规范化: 读取 selectConfirmedByProductId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @param userId 所属用户 ID，用于限定个人数据权限和查询范围。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<Order> selectConfirmedByProductId(@Param("productId") Long productId, @Param("userId") Long userId);
    
    /**
     * 业务注释规范化: 写入 insert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param order order 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int insert(Order order);
    /**
     * 业务注释规范化: 更新 update 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param order order 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int update(Order order);
}

