package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.NavMapper;
import com.timelordtty.dca.model.Nav;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 净值服务
 */
@Service
public class NavService {

    /**
     * 基金净值 Mapper，负责按产品和日期维护净值、分红和日收益率。
     */
    private final NavMapper navMapper;

    /**
     * 装配净值 Mapper，用于基金净值记录的新增、更新和按产品日期查询。
     */
    public NavService(NavMapper navMapper) {
        this.navMapper = navMapper;
    }

    /**
     * 获取历史净值
     */
    public List<Nav> getHistoryNav(Long productId, LocalDate startDate, LocalDate endDate) {
        return navMapper.selectByProductId(productId, startDate, endDate);
    }

    /**
     * 获取最新净值
     */
    public Nav getLatestNav(Long productId) {
        return navMapper.selectLatest(productId);
    }

    /**
     * 获取指定日期的净值
     */
    public Nav getNavByDate(Long productId, LocalDate navDate) {
        return navMapper.selectByDate(productId, navDate);
    }
}
