/**
 * 树形数据处理工具
 */

import type { Account } from '../types'

/**
 * 展平树形结构为扁平列表
 */
function flattenAccountTree(accounts: Account[]): Account[] {
  const result: Account[] = []
  
  function traverse(account: Account) {
    const { children, ...accountWithoutChildren } = account
    result.push(accountWithoutChildren as Account)
    if (children && children.length > 0) {
      children.forEach(traverse)
    }
  }
  
  accounts.forEach(traverse)
  return result
}

/**
 * 检测输入是否已经是树形结构
 */
function isTreeStructure(accounts: Account[]): boolean {
  // 如果数组为空，无法判断，返回false
  if (accounts.length === 0) return false
  
  // 检查是否有账户包含children字段（即使为空数组也算）
  const hasChildrenField = accounts.some(acc => 'children' in acc)
  
  // 检查是否所有账户都是根节点（parentAccountId为null/undefined）
  // 如果是树形结构，数组中的每个元素都应该是根节点
  const allAreRootNodes = accounts.every(acc => !acc.parentAccountId)
  
  // 如果所有账户都是根节点，且至少有一个账户有children字段，说明是树形结构
  // 这是因为扁平列表中，子账户的parentAccountId不为null，不会都在根节点
  return hasChildrenField && allAreRootNodes
}

/**
 * 构建账户树形结构
 * 支持两种输入格式：
 * 1. 扁平列表（所有账户在同一层级，通过parentAccountId关联）
 * 2. 树形结构（已经包含children字段的嵌套结构）
 */
export function buildAccountTree(accounts: Account[]): Account[] {
  // 如果输入已经是树形结构，直接返回
  if (isTreeStructure(accounts)) {
    return accounts
  }

  // 否则按扁平列表处理
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
 * 判断是否为信贷账户类型
 */
function isCreditAccountType(accountType: string): boolean {
  return accountType === 'CREDIT_CARD' || 
         accountType === 'HUABEI' || 
         accountType === 'BAITIAO' ||
         accountType === 'LOAN'
}

/**
 * 计算父账户聚合余额（所有子账户余额之和，排除信贷账户）
 * 信贷账户（CREDIT_CARD/HUABEI/BAITIAO/LOAN）不计入余额，因为它们的余额是欠款，不是资产
 */
export function calculateParentBalance(account: Account): number {
  if (!account.children || account.children.length === 0) {
    // 如果是信贷账户类型，不计入余额
    if (isCreditAccountType(account.accountType)) {
      return 0
    }
    return account.balance || 0
  }
  return account.children.reduce((sum, child) => {
    // 排除信贷账户
    if (isCreditAccountType(child.accountType)) {
      return sum
    }
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
