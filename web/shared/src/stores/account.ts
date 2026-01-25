/**
 * 账户Store
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { accountApi } from '../api'
import { buildAccountTree, getLeafAccounts } from '../utils'
import type { Account } from '../types'

export const useAccountStore = defineStore('account', () => {
  const accounts = ref<Account[]>([])
  const loading = ref(false)

  /**
   * 账户树
   * 后端返回的是扁平列表（所有账户），需要构建树形结构
   */
  const accountTree = computed(() => {
    return buildAccountTree(accounts.value)
  })

  /**
   * 现金叶子账户
   * 从账户树中递归获取所有叶子账户，确保准确性
   * 只包含没有子账户的账户（叶子账户）
   */
  const cashLeafAccounts = computed(() => {
    // 使用账户树来获取所有叶子账户（更可靠）
    const allLeafAccounts = getLeafAccounts(accountTree.value)
    
    // 过滤条件：只返回REAL类型的资产类账户
    const assetTypes = new Set(['CASH', 'BANK', 'PAYMENT', 'MMF', 'BROKER'])
    const filtered = allLeafAccounts.filter((acc) => {
      // 再次确认是叶子账户（没有子账户）
      if (acc.children && acc.children.length > 0) {
        console.warn('发现非叶子账户被包含在 cashLeafAccounts 中:', acc)
        return false
      }
      // 仅 REAL 账户参与现金叶子统计
      if (acc.accountKind !== 'REAL') return false
      // 只统计资产类账户（信贷账户如 CREDIT_CARD/HUABEI/BAITIAO/LOAN 视为负债，不计入可用资金）
      if (!assetTypes.has(acc.accountType)) return false
      return true
    })
    
    return filtered
  })

  /**
   * 获取账户列表
   */
  async function fetchAccounts(params?: { ownerType?: 'PERSONAL' | 'FAMILY' }) {
    loading.value = true
    try {
      accounts.value = await accountApi.getAccounts(params)
    } finally {
      loading.value = false
    }
  }

  /**
   * 获取账户详情
   */
  async function fetchAccount(id: number) {
    return await accountApi.getAccount(id)
  }

  /**
   * 创建账户
   */
  async function createAccount(data: Partial<Account>) {
    const newAccount = await accountApi.createAccount(data)
    await fetchAccounts() // 刷新列表
    return newAccount
  }

  /**
   * 更新账户
   */
  async function updateAccount(id: number, data: Partial<Account>) {
    const updatedAccount = await accountApi.updateAccount(id, data)
    await fetchAccounts() // 刷新列表
    return updatedAccount
  }

  /**
   * 调整账户余额
   */
  async function adjustBalance(id: number, balance: number, note?: string) {
    const updatedAccount = await accountApi.adjustBalance(id, { balance, note })
    await fetchAccounts() // 刷新列表
    return updatedAccount
  }

  /**
   * 根据账户ID获取账户
   */
  function getAccountById(id: number): Account | undefined {
    return accounts.value.find((a) => a.id === id)
  }

  /**
   * 获取所有叶子账户（用于记账校验）
   */
  function getAllLeafAccounts(): Account[] {
    return getLeafAccounts(accountTree.value)
  }

  return {
    accounts,
    accountTree,
    cashLeafAccounts,
    loading,
    fetchAccounts,
    fetchAccount,
    createAccount,
    updateAccount,
    adjustBalance,
    getAccountById,
    getAllLeafAccounts,
  }
})
