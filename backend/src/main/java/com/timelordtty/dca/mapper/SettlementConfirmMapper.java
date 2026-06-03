package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.SettlementConfirm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SettlementConfirmMapper 组件，定义 MyBatis SQL 映射，返回持久化对象或统计视图。
 */
@Mapper
public interface SettlementConfirmMapper {
    SettlementConfirm selectByOrderId(@Param("orderId") String orderId);
    int insert(SettlementConfirm settlement);
}

