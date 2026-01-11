/**
 * 枚举值到中文的映射常量
 * 所有下拉框和显示都使用这些映射来显示中文
 */

// 资产类型映射
export const assetTypeMap: Record<string, string> = {
  ETF: '交易型开放式指数基金',
  LOF: '上市型开放式基金',
  FUND: '普通基金',
  MMF: '货币基金',
  BANK_WM_NAV: '银行理财净值型',
  BANK_WM_BOX: '银行理财预期收益型',
  STOCK: '股票',
  FUTURES: '期货',
  OPTIONS: '期权',
  BOND_REPO: '国债逆回购',
}

// 账户类型映射
export const accountTypeMap: Record<string, string> = {
  BANK: '银行',
  PAYMENT: '支付平台',
  BROKER: '券商',
  MMF: '货币基金',
  CASH: '现金',
  CREDIT_CARD: '信用卡',
  HUABEI: '花呗',
  BAITIAO: '白条',
  LOAN: '贷款',
  OTHER: '其他',
}

// 账户性质映射
export const accountKindMap: Record<string, string> = {
  REAL: '现实账户',
  VIRTUAL: '虚拟科目',
}

// 资金用途映射
export const fundUsageMap: Record<string, string> = {
  SPENDABLE: '可支出',
  RESERVED: '专款',
  INVESTABLE: '可投资',
}

// 虚拟科目子类型映射
export const virtualSubtypeMap: Record<string, string> = {
  POSITION: '持仓',
  FEE: '费用',
  INCOME: '收入',
  EXPENSE: '支出',
  RECEIVABLE: '应收',
  LIABILITY: '负债',
}

// 交易类型映射
export const txnTypeMap: Record<string, string> = {
  BUY: '买入',
  SELL: '卖出',
  SUBSCRIPTION: '申购',
  REDEMPTION: '赎回',
  CUSTODY_TRANSFER: '转托管',
  BOND_REPO: '国债逆回购',
  DIVIDEND_CASH: '现金分红',
  DIVIDEND_REINVEST: '红利再投资',
  DIVIDEND_EX_DATE: '除权除息日',
  DIVIDEND_PAY_DATE: '分红发放日',
  INTEREST: '利息',
  FEE: '手续费',
  TAX: '税费',
  TRANSFER_OUT: '转出',
  TRANSFER_IN: '转入',
  EXPENSE: '支出',
  INCOME: '收入',
  ADJUST: '调整',
  REIMBURSE_IN: '报销收入',
  REIMBURSE_OUT: '报销支出',
  DEFER: '跨期结算',
}

// 订单类型映射
export const orderTypeMap: Record<string, string> = {
  BUY: '买入',
  SELL: '卖出',
  SUBSCRIPTION: '申购',
  REDEMPTION: '赎回',
}

// 订单状态映射
export const orderStatusMap: Record<string, string> = {
  PENDING: '待结算',
  CONFIRMED: '已确认',
  CANCELLED: '已取消',
  FAILED: '失败',
}

// 交易状态映射
export const txnStatusMap: Record<string, string> = {
  PENDING: '待确认',
  CONFIRMED: '已确认',
  CANCELLED: '已取消',
  REVERSED: '已撤销',
}

// 渠道映射
export const channelMap: Record<string, string> = {
  EXCHANGE: '场内',
  OTC: '场外',
}

// 市场映射
export const marketMap: Record<string, string> = {
  SH: '上海',
  SZ: '深圳',
  NA: '不适用',
}

// 货币映射
export const currencyMap: Record<string, string> = {
  CNY: '人民币',
  USD: '美元',
  HKD: '港币',
}

// 归属类型映射
export const ownerTypeMap: Record<string, string> = {
  PERSONAL: '个人',
  FAMILY: '家庭',
}

// 关联类型映射
export const relationTypeMap: Record<string, string> = {
  NONE: '无关联',
  TRANSFER_PAIR: '转账成对',
  REFUND: '退款',
  REFUND_OF: '退款属于原交易',
  REIMBURSE: '报销',
  REIMBURSEMENT_OF: '报销属于原支出',
  REVERSAL: '撤销',
  CUSTODY_TRANSFER_OF: '转托管属于原事件',
}

// 借贷方向映射
export const postingTypeMap: Record<string, string> = {
  DEBIT: '借方',
  CREDIT: '贷方',
}

// 角色映射
export const roleMap: Record<string, string> = {
  ADMIN: '管理员',
  MEMBER: '成员',
}
