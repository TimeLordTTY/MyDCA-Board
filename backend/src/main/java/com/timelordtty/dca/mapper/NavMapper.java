package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.Nav;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
/**
 * 业务注释规范化: NavMapper Mapper 接口，负责 MyBatis SQL 映射和持久化访问，真实业务规则由服务层保证。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public interface NavMapper {
    List<Nav> selectByProductId(@Param("productId") Long productId, 
                                @Param("startDate") LocalDate startDate, 
                                @Param("endDate") LocalDate endDate);
    /**
     * 业务注释规范化: 读取 selectLatest 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    Nav selectLatest(@Param("productId") Long productId);
    /**
     * 业务注释规范化: 读取 selectByDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @param navDate navDate 日期字段，用于交易、确认、净值或统计周期口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    Nav selectByDate(@Param("productId") Long productId, @Param("navDate") LocalDate navDate);
    /**
     * 业务注释规范化: 写入 insert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param nav 产品净值，用于按份额折算市值、收益或确认金额。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int insert(Nav nav);
    /**
     * 业务注释规范化: 更新 update 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param nav 产品净值，用于按份额折算市值、收益或确认金额。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int update(Nav nav);
}
