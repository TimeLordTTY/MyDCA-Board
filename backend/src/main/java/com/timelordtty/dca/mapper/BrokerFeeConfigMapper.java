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
public interface BrokerFeeConfigMapper {
    
    /**
     * 根据ID查询费率配置
     * 
     * @param id 费率配置ID
     * @return 费率配置对象
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
    List<BrokerFeeConfig> selectByAccountId(@Param("accountId") Long accountId);
    
    /**
     * 插入费率配置
     * 
     * @param config 费率配置对象
     * @return 影响行数
     */
    int insert(BrokerFeeConfig config);
    
    /**
     * 更新费率配置
     * 
     * @param config 费率配置对象
     * @return 影响行数
     */
    int update(BrokerFeeConfig config);
    
    /**
     * 删除费率配置
     * 
     * @param id 费率配置ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
