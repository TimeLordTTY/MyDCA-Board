/**
 * 人工结算预览文案工具：让主人在二次确认前看懂现金 / 持仓 / 手续费影响。
 */

import type { SettlementPreview } from '../types'

/** 结算预览中文文案（含订单、产品、输入参数与逐条结算影响）。 */
export function buildSettlementPreviewText(preview: SettlementPreview | null | undefined): string {
  if (!preview) {
    return ''
  }
  const lines: string[] = []
  lines.push(`${preview.orderTypeLabel || preview.orderType || '结算'} · 订单 ${preview.orderId}`)
  if (preview.productName) {
    lines.push(`产品：${preview.productName}${preview.productCode ? `（${preview.productCode}）` : ''}`)
  }
  if (preview.confirmNav !== undefined && preview.confirmNav !== null) {
    lines.push(`确认净值：${preview.confirmNav}`)
  }
  if (preview.confirmShares !== undefined && preview.confirmShares !== null) {
    lines.push(`确认份额：${preview.confirmShares}`)
  }
  if (preview.confirmAmount !== undefined && preview.confirmAmount !== null) {
    lines.push(`确认金额：${preview.confirmAmount}`)
  }
  if (preview.confirmFee !== undefined && preview.confirmFee !== null) {
    lines.push(`手续费：${preview.confirmFee}`)
  }
  if (preview.summaryLines && preview.summaryLines.length > 0) {
    lines.push('', '结算影响：', ...preview.summaryLines.map((line) => `· ${line}`))
  }
  return lines.join('\n')
}

/** 结算预览阻断原因中文文案。 */
export function buildSettlementBlockingText(preview: SettlementPreview | null | undefined): string {
  const reasons = preview?.blockingReasons ?? []
  return reasons.length > 0
    ? reasons.join('；')
    : '结算预览未通过校验，请检查订单状态、资金来源与输入参数'
}

/** 二次确认弹窗标题。 */
export function buildSettlementConfirmTitle(preview: SettlementPreview | null | undefined): string {
  if (!preview) {
    return '确认结算'
  }
  return `确认结算：${preview.orderTypeLabel || preview.orderType || ''} ${preview.productName || preview.orderId}`
}