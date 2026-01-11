package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.IndicatorDailyMapper;
import com.timelordtty.dca.model.IndicatorDaily;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 指标服务
 */
@Service
public class IndicatorService {

    private final IndicatorDailyMapper indicatorDailyMapper;

    public IndicatorService(IndicatorDailyMapper indicatorDailyMapper) {
        this.indicatorDailyMapper = indicatorDailyMapper;
    }

    /**
     * 获取历史指标数据
     */
    public List<IndicatorDaily> getHistoryIndicators(Long productId, LocalDate startDate, LocalDate endDate, Integer windowDays) {
        return indicatorDailyMapper.selectByProductId(productId, startDate, endDate, windowDays);
    }

    /**
     * 获取最新指标数据
     */
    public IndicatorDaily getLatestIndicator(Long productId, Integer windowDays) {
        return indicatorDailyMapper.selectLatest(productId, windowDays);
    }
}
