package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.BrokerFeeConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 券商费率配置Mapper接口
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Mapper
/**
 * 业务注释规范化: BrokerFeeConfigMapper Mapper 接口，负责 MyBatis SQL 映射和持久化访问，真实业务规则由服务层保证。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public interface BrokerFeeConfigMapper {
    
    /**
     * 根据ID查询费率配置
     * 
     * @param id 费率配置ID
     * @return 费率配置对象
     */
    /**
     * 业务注释规范化: 读取 selectById 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param id 主键 ID，用于在后端内部唯一定位该业务记录。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    BrokerFeeConfig selectById(@Param("id") Long id);
    
    /**
     * 根据券商账户ID和费率规则类型查询费率配置
     * 
     * @param accountId 券商账户ID
     * @param feeRuleType 费率规则类型
     * @return 费率配置对象
     */
    BrokerFeeConfig selectByAccountAndRuleType(@Param("accountId") Long accountId, 
                                               @Param("feeRuleType") String feeRuleType);
    
    /**
     * 根据券商账户ID查询所有费率配置
     * 
     * @param accountId 券商账户ID
     * @return 费率配置列表
     */
    /**
     * 业务注释规范化: 读取 selectByAccountId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param accountId 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    List<BrokerFeeConfig> selectByAccountId(@Param("accountId") Long accountId);
    
    /**
     * 插入费率配置
     * 
     * @param config 费率配置对象
     * @return 影响行数
     */
    /**
     * 业务注释规范化: 写入 insert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param config config 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int insert(BrokerFeeConfig config);
    
    /**
     * 更新费率配置
     * 
     * @param config 费率配置对象
     * @return 影响行数
     */
    /**
     * 业务注释规范化: 更新 update 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param config config 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int update(BrokerFeeConfig config);
    
    /**
     * 删除费率配置
     * 
     * @param id 费率配置ID
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
}
