/**
 * 树形数据处理工具
 */

import type { Account } from '../types'

/**
 * 构建账户树形结构
 */
export function buildAccountTree(accounts: Account[]): Account[] {
  const accountMap = new Map<number, Account>()
  const rootAccounts: Account[] = []

  // 第一遍：创建所有账户的映射，并初始化children数组
  accounts.forEach((account) => {
    accountMap.set(account.id, { ...account, children: [] })
  })

  // 第二遍：构建父子关系
  accounts.forEach((account) => {
    const node = accountMap.get(account.id)!
    if (account.parentAccountId) {
      const parent = accountMap.get(account.parentAccountId)
      if (parent) {
        if (!parent.children) {
          parent.children = []
        }
        parent.children.push(node)
      }
    } else {
      rootAccounts.push(node)
    }
  })

  return rootAccounts
}

/**
 * 计算父账户聚合余额（所有子账户余额之和）
 */
export function calculateParentBalance(account: Account): number {
  if (!account.children || account.children.length === 0) {
    return account.balance || 0
  }
  return account.children.reduce((sum, child) => {
    return sum + (child.balance || 0)
  }, 0)
}

/**
 * 计算父账户占用金额（所有子账户占用之和）
 */
export function calculateParentReservedAmount(account: Account): number {
  if (!account.children || account.children.length === 0) {
    return account.reservedAmount || 0
  }
  return account.children.reduce((sum, child) => {
    return sum + (child.reservedAmount || 0)
  }, 0)
}

/**
 * 获取所有叶子账户
 */
export function getLeafAccounts(accounts: Account[]): Account[] {
  const leafAccounts: Account[] = []
  
  function traverse(account: Account) {
    if (!account.children || account.children.length === 0) {
      leafAccounts.push(account)
    } else {
      account.children.forEach(traverse)
    }
  }
  
  accounts.forEach(traverse)
  return leafAccounts
}
