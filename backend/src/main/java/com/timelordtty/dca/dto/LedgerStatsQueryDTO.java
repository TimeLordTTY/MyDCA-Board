package com.timelordtty.dca.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 流水统计查询条件。
 *
 * <p>该 DTO 只承载统计筛选口径，不代表账本入账指令，也不会触发账户余额、持仓成本或订单状态变更。</p>
 */
@Data
public class LedgerStatsQueryDTO {
    /** 当前统计所属用户 ID；后端会结合 scope 决定个人或家庭视角。 */
    private Long userId;
    /** 家庭统计视角下的家庭 ID，个人视角为空。 */
    private Long familyId;
    /** 统计范围，默认 PERSONAL；家庭视角由服务层填充 familyId 并做权限校验。 */
    private String scope = "PERSONAL";
    /** 统计开始日，按流水 tradeDate 过滤，包含当天。 */
    private LocalDate startDate;
    /** 统计结束日，按流水 tradeDate 过滤，包含当天。 */
    private LocalDate endDate;
    /** 需要纳入统计的流水类型集合，例如 EXPENSE、INCOME、BUY、SELL。 */
    private List<String> txnTypes;
    /** 直接选择的账户 ID，用于限定某些具体账户的流水分录。 */
    private List<Long> accountIds;
    /** 父账户 ID 集合，服务层会展开为叶子账户后再进入统计。 */
    private List<Long> parentAccountIds;
    /** accountIds 与 parentAccountIds 展开后的实际账户集合，供 Mapper 查询使用。 */
    private List<Long> effectiveAccountIds;
    /** 收入/支出分类 ID 集合，用于分类维度过滤。 */
    private List<Long> categoryIds;
    /** 一级分类名称筛选，仅用于统计展示口径，不改变分类主数据。 */
    private String categoryL1;
    /** 二级分类名称筛选，仅用于统计展示口径，不改变分类主数据。 */
    private String categoryL2;
    /** 产品 ID 集合，用于限定与基金、ETF 等投资产品相关的流水。 */
    private List<Long> productIds;
    /** 是否把转账流水纳入统计；默认排除，避免把内部账户搬移误算成收支。 */
    private Boolean includeTransfer = false;
    /** 趋势统计周期，默认 MONTH，可由服务层归一化为日、周、月等展示粒度。 */
    private String period = "MONTH";
    /** 分组维度，默认 CATEGORY；可按账户、产品、流水类型等维度聚合。 */
    private String groupBy = "CATEGORY";
    /** TopN 或明细限制数量，默认 10，避免统计接口返回过大列表。 */
    private Integer limit = 10;
}
