package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.SettlementConfirm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SettlementConfirmMapper {
    SettlementConfirm selectByOrderId(@Param("orderId") String orderId);
    int insert(SettlementConfirm settlement);
}

