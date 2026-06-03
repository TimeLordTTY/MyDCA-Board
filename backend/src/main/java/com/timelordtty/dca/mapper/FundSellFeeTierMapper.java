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
/**
 * 业务注释规范化: FundSellFeeTierMapper Mapper 接口，负责 MyBatis SQL 映射和持久化访问，真实业务规则由服务层保证。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public interface FundSellFeeTierMapper {
    
    /**
     * 根据ID查询费率分段
     * 
     * @param id 费率分段ID
     * @return 费率分段对象
     */
    /**
     * 业务注释规范化: 读取 selectById 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    FundSellFeeTier selectById(@Param("id") Long id);
    
    /**
     * 根据产品ID查询所有费率分段（按sort_order排序）
     * 
     * @param productId 产品ID
     * @return 费率分段列表
     */
    /**
     * 业务注释规范化: 读取 selectByProductId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
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
    /**
     * 业务注释规范化: 写入 insert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param tier tier 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int insert(FundSellFeeTier tier);
    
    /**
     * 更新费率分段
     * 
     * @param tier 费率分段对象
     * @return 影响行数
     */
    /**
     * 业务注释规范化: 更新 update 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param tier tier 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int update(FundSellFeeTier tier);
    
    /**
     * 删除费率分段
     * 
     * @param id 费率分段ID
     * @return 影响行数
     */
    /**
     * 业务注释规范化: 删除 deleteById 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 根据产品ID删除所有费率分段
     * 
     * @param productId 产品ID
     * @return 影响行数
     */
    /**
     * 业务注释规范化: 删除 deleteByProductId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int deleteByProductId(@Param("productId") Long productId);
}
