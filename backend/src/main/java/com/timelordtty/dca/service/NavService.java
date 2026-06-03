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
/**
 * 业务注释规范化: NavService 服务类，负责业务规则、账户、账本流水、订单或持仓数据的组合处理。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class NavService {

    /**
     * 业务注释规范化: navMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    private final NavMapper navMapper;

    /**
     * 业务注释规范化: 处理 NavService 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param navMapper navMapper 业务字段，承载该对象在后端流程中的核心属性。
     */
    public NavService(NavMapper navMapper) {
        this.navMapper = navMapper;
    }

    /**
     * 获取历史净值
     */
    /**
     * 业务注释规范化: 查询 getHistoryNav 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @param startDate startDate 日期字段，用于交易、确认、净值或统计周期口径。
     * @param endDate endDate 日期字段，用于交易、确认、净值或统计周期口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public List<Nav> getHistoryNav(Long productId, LocalDate startDate, LocalDate endDate) {
        return navMapper.selectByProductId(productId, startDate, endDate);
    }

    /**
     * 获取最新净值
     */
    /**
     * 业务注释规范化: 查询 getLatestNav 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public Nav getLatestNav(Long productId) {
        return navMapper.selectLatest(productId);
    }

    /**
     * 获取指定日期的净值
     */
    /**
     * 业务注释规范化: 查询 getNavByDate 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param productId 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     * @param navDate navDate 日期字段，用于交易、确认、净值或统计周期口径。
     * @return 处理后的业务结果，具体结构保持现有契约不变。
     */
    public Nav getNavByDate(Long productId, LocalDate navDate) {
        return navMapper.selectByDate(productId, navDate);
    }
}
