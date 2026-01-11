/**
 * 枚举值转换工具函数
 */

import {
  assetTypeMap,
  accountTypeMap,
  accountKindMap,
  fundUsageMap,
  virtualSubtypeMap,
  txnTypeMap,
  orderTypeMap,
  orderStatusMap,
  txnStatusMap,
  channelMap,
  marketMap,
  currencyMap,
  ownerTypeMap,
  relationTypeMap,
  postingTypeMap,
  roleMap,
} from '../constants'

/**
 * 获取资产类型中文标签
 */
export function getAssetTypeLabel(value: string | null | undefined): string {
  if (!value) return ''
  return assetTypeMap[value] || value
}

/**
 * 获取账户类型中文标签
 */
export function getAccountTypeLabel(value: string | null | undefined): string {
  if (!value) return ''
  return accountTypeMap[value] || value
}

/**
 * 获取账户性质中文标签
 */
export function getAccountKindLabel(value: string | null | undefined): string {
  if (!value) return ''
  return accountKindMap[value] || value
}

/**
 * 获取资金用途中文标签
 */
export function getFundUsageLabel(value: string | null | undefined): string {
  if (!value) return ''
  return fundUsageMap[value] || value
}

/**
 * 获取虚拟科目子类型中文标签
 */
export function getVirtualSubtypeLabel(value: string | null | undefined): string {
  if (!value) return ''
  return virtualSubtypeMap[value] || value
}

/**
 * 获取交易类型中文标签
 */
export function getTxnTypeLabel(value: string | null | undefined): string {
  if (!value) return ''
  return txnTypeMap[value] || value
}

/**
 * 获取订单类型中文标签
 */
export function getOrderTypeLabel(value: string | null | undefined): string {
  if (!value) return ''
  return orderTypeMap[value] || value
}

/**
 * 获取订单状态中文标签
 */
export function getOrderStatusLabel(value: string | null | undefined): string {
  if (!value) return ''
  return orderStatusMap[value] || value
}

/**
 * 获取交易状态中文标签
 */
export function getTxnStatusLabel(value: string | null | undefined): string {
  if (!value) return ''
  return txnStatusMap[value] || value
}

/**
 * 获取渠道中文标签
 */
export function getChannelLabel(value: string | null | undefined): string {
  if (!value) return ''
  return channelMap[value] || value
}

/**
 * 获取市场中文标签
 */
export function getMarketLabel(value: string | null | undefined): string {
  if (!value) return ''
  return marketMap[value] || value
}

/**
 * 获取货币中文标签
 */
export function getCurrencyLabel(value: string | null | undefined): string {
  if (!value) return ''
  return currencyMap[value] || value
}

/**
 * 获取归属类型中文标签
 */
export function getOwnerTypeLabel(value: string | null | undefined): string {
  if (!value) return ''
  return ownerTypeMap[value] || value
}

/**
 * 获取关联类型中文标签
 */
export function getRelationTypeLabel(value: string | null | undefined): string {
  if (!value) return ''
  return relationTypeMap[value] || value
}

/**
 * 获取借贷方向中文标签
 */
export function getPostingTypeLabel(value: string | null | undefined): string {
  if (!value) return ''
  return postingTypeMap[value] || value
}

/**
 * 获取角色中文标签
 */
export function getRoleLabel(value: string | null | undefined): string {
  if (!value) return ''
  return roleMap[value] || value
}
