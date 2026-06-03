package com.timelordtty.dca.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
/**
 * 业务注释规范化: LedgerStatsPostingDTO DTO 数据传输对象，用于承载请求参数或响应结果，属于前后端契约。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class LedgerStatsPostingDTO {
    /**
     * 业务注释规范化: 流水业务 ID，用于聚合一笔复式记账交易下的所有分录。
     */
    private String txnId;
    /**
     * 业务注释规范化: 流水类型，决定该笔交易在收入、支出、投资、转账等统计口径中的归类。
     */
    private String txnType;
    /**
     * 业务注释规范化: 所属用户 ID，用于限定个人数据权限和查询范围。
     */
    private Long userId;
    /**
     * 业务注释规范化: 所属家庭 ID，用于家庭视角下的数据隔离。
     */
    private Long familyId;
    /**
     * 业务注释规范化: 关联产品 ID，用于把流水、订单、持仓或行情绑定到具体投资产品。
     */
    private Long productId;
    /**
     * 业务注释规范化: requestedAt 时间字段，用于记录业务动作发生或审计时间。
     */
    private LocalDateTime requestedAt;
    /**
     * 业务注释规范化: tradeDate 日期字段，用于交易、确认、净值或统计周期口径。
     */
    private LocalDate tradeDate;
    /**
     * 业务注释规范化: 业务状态，表示记录当前所处的创建、确认、取消或完成阶段。
     */
    private String status;
    /**
     * 业务注释规范化: categoryId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     */
    private Long categoryId;
    /**
     * 业务注释规范化: isReimbursable 布尔标记，用于控制该记录在业务流程中的特殊状态。
     */
    private Boolean isReimbursable;
    /**
     * 业务注释规范化: isReimbursed 布尔标记，用于控制该记录在业务流程中的特殊状态。
     */
    private Boolean isReimbursed;
    /**
     * 业务注释规范化: isReversed 布尔标记，用于控制该记录在业务流程中的特殊状态。
     */
    private Boolean isReversed;
    /**
     * 业务注释规范化: note 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String note;
    /**
     * 业务注释规范化: postingId 关联 ID，用于连接对应业务对象并保持数据引用关系。
     */
    private Long postingId;
    /**
     * 业务注释规范化: 分录方向，DEBIT/CREDIT 决定账户余额在复式记账中的增减方向。
     */
    private String postingType;
    /**
     * 业务注释规范化: 关联账户 ID，指向承载资金、持仓或虚拟科目的账户。
     */
    private Long accountId;
    /**
     * 业务注释规范化: accountName 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String accountName;
    /**
     * 业务注释规范化: accountKind 类型字段，用于区分不同业务分类并驱动处理分支。
     */
    private String accountKind;
    /**
     * 业务注释规范化: accountType 类型字段，用于区分不同业务分类并驱动处理分支。
     */
    private String accountType;
    /**
     * 业务注释规范化: 父级账户 ID，用于表达平台账户与资金分区的层级关系。
     */
    private Long parentAccountId;
    /**
     * 业务注释规范化: parentAccountName 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String parentAccountName;
    /**
     * 业务注释规范化: 业务金额，通常以账户币种计价，正负含义由交易类型和分录方向决定。
     */
    private BigDecimal amount;
    /**
     * 业务注释规范化: 产品份额，适用于基金、ETF、货币基金等按份额管理的资产。
     */
    private BigDecimal shares;
    /**
     * 业务注释规范化: currency 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String currency;
}
