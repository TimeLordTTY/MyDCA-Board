package com.timelordtty.dca.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品主数据表实体（product_master表）
 * 
 * 对应数据库表：product_master
 * 
 * 设计说明：保存产品（基金/ETF/股票/逆回购等）的基础元数据，用于前端展示、下单与风控校验
 * 
 * 字段说明：
 * - id: 产品ID，主键，自增
 * - productCode: 产品代码，交易所代码或内部编码，唯一标识
 * - channel: 渠道：EXCHANGE=场内（交易所），OTC=场外（银行/基金公司）
 * - market: 市场标识：SH=上海，SZ=深圳，NA=不适用（场外产品）
 * - assetType: 资产类型：ETF/LOF/FUND/MMF/BANK_WM_NAV/BANK_WM_BOX/STOCK/FUTURES/OPTIONS/BOND_REPO
 * - currency: 币种：CNY/USD/HKD，默认CNY
 * - productName: 产品名称，用户显示名称
 * - isQdii: 是否QDII产品，true=是，false=否
 * - trackIndex: 跟踪指数（若为被动基金），如"沪深300"
 * - buyFeeRate: 买入费率，买入时的手续费率（如0.0015表示0.15%）
 * - sellFeeRate: 卖出费率，卖出时的手续费率
 * - buyConfirmOffset: 买入确认偏移日，交易到确认的天数（T+N中的N），买入/申购时使用
 * - sellConfirmOffset: 卖出确认偏移日，交易到确认的天数（T+N中的N），卖出/赎回时使用
 * - cutoffTime: 交易截单时间，字符串表示（如"15:00"），超过此时间算下一个交易日
 * - dataSource: 数据来源标识，如"AKSHARE"、"FUND"等
 * - isActive: 是否启用，true=启用，false=禁用，默认true
 * - createdAt: 创建时间
 * - updatedAt: 更新时间，自动更新
 * 
 * 业务说明：
 * 1. 对于不同 assetType，系统在下单/结算时会有不同的处理逻辑（例如净值类基金需要根据 nav 计算份额）
 * 2. BOND_REPO（国债逆回购）在系统中以特殊规则处理：
 *    - 下单处理：创建REPO订单（PENDING），仅增加source_account.reserved_amount（锁定本金），不生成ledger_txn和ledger_posting
 *    - 到期处理：确认订单（CONFIRMED），必须按顺序：1) 校验订单到期；2) 释放占用（accounts.reserved_amount -= principal）；3) 生成利息ledger_txn（CASH DEBIT(interest) + INCOME CREDIT(interest)，记到同一个发起子账户）
 *    - 不生成持仓POSITION，最贴合"现金管理"场景
 * 3. 净值类产品（FUND/MMF等）需要根据nav计算份额：shares = amount / nav
 * 4. 场内产品（ETF/LOF/STOCK等）使用实时价格，场外产品使用净值
 * 
 * 费率计算：
 * - 买入手续费 = amount × buyFeeRate
 * - 卖出手续费 = amount × sellFeeRate
 * - 实际到账金额 = amount - 手续费
 * 
 * @author timelordtty
 * @since 1.0.0
 */
@Data
/**
 * 业务注释规范化: ProductMaster 实体模型，对应后端数据库中的核心业务记录。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class ProductMaster {
    /** 产品ID，主键，自增 */
    /**
     * 业务注释规范化: 主键 ID，用于在后端内部唯一定位该业务记录。
     */
    private Long id;
    
    /** 产品代码，交易所代码或内部编码，唯一标识 */
    /**
     * 业务注释规范化: productCode 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String productCode;
    
    /** 渠道：EXCHANGE=场内（交易所），OTC=场外（银行/基金公司） */
    /**
     * 业务注释规范化: channel 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String channel;
    
    /** 市场标识：SH=上海，SZ=深圳，NA=不适用（场外产品） */
    /**
     * 业务注释规范化: market 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String market;
    
    /** 资产类型：ETF/LOF/FUND/MMF/BANK_WM_NAV/BANK_WM_BOX/STOCK/FUTURES/OPTIONS/BOND_REPO */
    /**
     * 业务注释规范化: assetType 类型字段，用于区分不同业务分类并驱动处理分支。
     */
    private String assetType;
    
    /** 币种：CNY/USD/HKD，默认CNY */
    /**
     * 业务注释规范化: currency 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String currency;
    
    /** 产品名称，用户显示名称 */
    /**
     * 业务注释规范化: productName 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String productName;
    
    /** 是否QDII产品，true=是，false=否 */
    /**
     * 业务注释规范化: isQdii 布尔标记，用于控制该记录在业务流程中的特殊状态。
     */
    private Boolean isQdii;
    
    /** 跟踪指数（若为被动基金），如"沪深300" */
    /**
     * 业务注释规范化: trackIndex 类型字段，用于区分不同业务分类并驱动处理分支。
     */
    private String trackIndex;
    
    /** 买入费率，买入时的手续费率（如0.0015表示0.15%） */
    /**
     * 业务注释规范化: buyFeeRate 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal buyFeeRate;
    
    /** 卖出费率，卖出时的手续费率 */
    /**
     * 业务注释规范化: sellFeeRate 金额字段，用于表达该场景下的资金规模或费用口径。
     */
    private BigDecimal sellFeeRate;
    
    /** 买入确认偏移日，交易到确认的天数（T+N中的N），买入/申购时使用 */
    /**
     * 业务注释规范化: buyConfirmOffset 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Integer buyConfirmOffset;
    
    /** 卖出确认偏移日，交易到确认的天数（T+N中的N），卖出/赎回时使用 */
    /**
     * 业务注释规范化: sellConfirmOffset 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Integer sellConfirmOffset;
    
    /** 交易截单时间，字符串表示（如"15:00"），超过此时间算下一个交易日 */
    /**
     * 业务注释规范化: cutoffTime 时间字段，用于记录业务动作发生或审计时间。
     */
    private String cutoffTime;
    
    /** 数据来源标识，如"AKSHARE"、"FUND"等 */
    /**
     * 业务注释规范化: dataSource 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String dataSource;
    
    /** 是否启用，true=启用，false=禁用，默认true */
    /**
     * 业务注释规范化: isActive 布尔标记，用于控制该记录在业务流程中的特殊状态。
     */
    private Boolean isActive;
    
    /** 排序顺序（数字越小越靠前，NULL表示未设置） */
    /**
     * 业务注释规范化: sortOrder 业务字段，承载该对象在后端流程中的核心属性。
     */
    private Integer sortOrder;
    
    /** 备注 */
    /**
     * 业务注释规范化: note 业务字段，承载该对象在后端流程中的核心属性。
     */
    private String note;
    
    /** 创建时间 */
    /**
     * 业务注释规范化: 记录创建时间，用于审计和排序。
     */
    private LocalDateTime createdAt;
    
    /** 更新时间，自动更新 */
    /**
     * 业务注释规范化: 记录最后更新时间，用于审计和增量同步。
     */
    private LocalDateTime updatedAt;
}
