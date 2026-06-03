package com.timelordtty.dca.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
/**
 * 业务注释规范化: LedgerStatsQueryDTO DTO 数据传输对象，用于承载请求参数或响应结果，属于前后端契约。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class LedgerStatsQueryDTO {
    /**
     * 业务注释规范化: 所属用户 ID，用于限定个人数据权限和查询范围。
     */
    private Long userId;
    /**
     * 业务注释规范化: 所属家庭 ID，用于家庭视角下的数据隔离。
     */
    private Long familyId;
    /**
     * 业务注释规范化: scope 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String scope = "PERSONAL";
    /**
     * 业务注释规范化: startDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate startDate;
    /**
     * 业务注释规范化: endDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate endDate;
    /**
     * 业务注释规范化: txnTypes 类型字段，用于区分不同业务分类并驱动处理分支。
     */
    private List<String> txnTypes;
    /**
     * 业务注释规范化: accountIds 业务字段，承载该对象在后端流程中的核心属性。
     */
    private List<Long> accountIds;
    /**
     * 业务注释规范化: parentAccountIds 业务字段，承载该对象在后端流程中的核心属性。
     */
    private List<Long> parentAccountIds;
    /**
     * 业务注释规范化: effectiveAccountIds 业务字段，承载该对象在后端流程中的核心属性。
     */
    private List<Long> effectiveAccountIds;
    /**
     * 业务注释规范化: categoryIds 业务字段，承载该对象在后端流程中的核心属性。
     */
    private List<Long> categoryIds;
    /**
     * 业务注释规范化: categoryL1 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String categoryL1;
    /**
     * 业务注释规范化: categoryL2 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String categoryL2;
    /**
     * 业务注释规范化: productIds 业务字段，承载该对象在后端流程中的核心属性。
     */
    private List<Long> productIds;
    /**
     * 业务注释规范化: includeTransfer 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Boolean includeTransfer = false;
    /**
     * 业务注释规范化: period 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String period = "MONTH";
    /**
     * 业务注释规范化: groupBy 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String groupBy = "CATEGORY";
    /**
     * 业务注释规范化: limit 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Integer limit = 10;
}
