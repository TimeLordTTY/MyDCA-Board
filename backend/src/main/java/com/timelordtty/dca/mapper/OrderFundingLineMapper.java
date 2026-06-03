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
/**
 * 业务注释规范化: OrderFundingLineMapper Mapper 接口，负责 MyBatis SQL 映射和持久化访问，真实业务规则由服务层保证。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public interface OrderFundingLineMapper {
    
    /**
     * 根据订单ID查询所有资金来源行
     * 
     * @param orderId 订单ID
     * @return 资金来源行列表
     */
    /**
     * 业务注释规范化: 读取 selectByOrderId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param orderId 关联订单 ID，用于串联订单创建、资金冻结、结算确认和流水入账。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<OrderFundingLine> selectByOrderId(@Param("orderId") String orderId);
    
    /**
     * 插入资金来源行
     * 
     * @param fundingLine 资金来源行实体
     * @return 影响行数
     */
    /**
     * 业务注释规范化: 写入 insert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param fundingLine fundingLine 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int insert(OrderFundingLine fundingLine);
    
    /**
     * 批量插入资金来源行
     * 
     * @param fundingLines 资金来源行列表
     * @return 影响行数
     */
    /**
     * 业务注释规范化: 处理 batchInsert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param fundingLines fundingLines 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int batchInsert(@Param("fundingLines") List<OrderFundingLine> fundingLines);
    
    /**
     * 根据订单ID删除所有资金来源行（用于取消订单）
     * 
     * @param orderId 订单ID
     * @return 影响行数
     */
    /**
     * 业务注释规范化: 删除 deleteByOrderId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param orderId 关联订单 ID，用于串联订单创建、资金冻结、结算确认和流水入账。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int deleteByOrderId(@Param("orderId") String orderId);
}
