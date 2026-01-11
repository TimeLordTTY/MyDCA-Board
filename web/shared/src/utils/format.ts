/**
 * 格式化工具函数
 */

/**
 * 金额格式化（¥ 1,234.56）
 */
export function formatCurrency(amount: number | string | null | undefined): string {
  if (amount === null || amount === undefined || amount === '') {
    return '¥ 0.00'
  }
  const num = typeof amount === 'string' ? parseFloat(amount) : amount
  if (isNaN(num)) {
    return '¥ 0.00'
  }
  return `¥ ${num.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}

/**
 * 数字格式化（1,234.56）
 */
export function formatNumber(num: number | string | null | undefined, decimals: number = 2): string {
  if (num === null || num === undefined || num === '') {
    return '0.00'
  }
  const n = typeof num === 'string' ? parseFloat(num) : num
  if (isNaN(n)) {
    return '0.00'
  }
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })
}

/**
 * 日期格式化（YYYY-MM-DD）
 */
export function formatDate(date: string | Date | null | undefined): string {
  if (!date) {
    return ''
  }
  const d = typeof date === 'string' ? new Date(date) : date
  if (isNaN(d.getTime())) {
    return ''
  }
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * 日期时间格式化（YYYY-MM-DD HH:mm:ss）
 */
export function formatDateTime(date: string | Date | null | undefined): string {
  if (!date) {
    return ''
  }
  const d = typeof date === 'string' ? new Date(date) : date
  if (isNaN(d.getTime())) {
    return ''
  }
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

/**
 * 百分比格式化（12.34%）
 */
export function formatPercent(value: number | string | null | undefined, decimals: number = 2): string {
  if (value === null || value === undefined || value === '') {
    return '0.00%'
  }
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) {
    return '0.00%'
  }
  return `${num.toFixed(decimals)}%`
}
