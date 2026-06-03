package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.Nav;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface NavMapper {
    List<Nav> selectByProductId(@Param("productId") Long productId, 
                                @Param("startDate") LocalDate startDate, 
                                @Param("endDate") LocalDate endDate);
    Nav selectLatest(@Param("productId") Long productId);
    Nav selectByDate(@Param("productId") Long productId, @Param("navDate") LocalDate navDate);
    int insert(Nav nav);
    int update(Nav nav);
}
