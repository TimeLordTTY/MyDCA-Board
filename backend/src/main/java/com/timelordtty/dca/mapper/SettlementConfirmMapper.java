package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.SettlementConfirm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
/**
 * 业务注释规范化: SettlementConfirmMapper Mapper 接口，负责 MyBatis SQL 映射和持久化访问，真实业务规则由服务层保证。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public interface SettlementConfirmMapper {
    /**
     * 业务注释规范化: 读取 selectByOrderId 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param orderId 关联订单 ID，用于串联订单创建、资金冻结、结算确认和流水入账。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    SettlementConfirm selectByOrderId(@Param("orderId") String orderId);
    /**
     * 业务注释规范化: 写入 insert 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param settlement settlement 业务字段，承载该对象在后端流程中的核心属性。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    int insert(SettlementConfirm settlement);
}

