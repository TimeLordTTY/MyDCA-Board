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
   * 直接从扁平列表中提取叶子账户，避免重复处理
   */
  const cashLeafAccounts = computed(() => {
    // 获取所有账户ID集合（用于判断哪些账户是叶子账户）
    const allAccountIds = new Set(accounts.value.map(acc => acc.id))
    
    // 找出所有有父账户的账户ID（这些是子账户）
    const childAccountIds = new Set(
      accounts.value
        .filter(acc => acc.parentAccountId != null)
        .map(acc => acc.id)
    )
    
    // 找出所有父账户ID（这些账户有子账户）
    const parentAccountIds = new Set(
      accounts.value
        .filter(acc => acc.parentAccountId != null)
        .map(acc => acc.parentAccountId!)
    )
    
    // 叶子账户 = 没有子账户的账户（不在parentAccountIds中）
    const assetTypes = new Set(['CASH', 'BANK', 'PAYMENT', 'MMF', 'BROKER'])
    const leafAccounts = accounts.value.filter((acc) => {
      // 叶子账户：没有子账户
      if (parentAccountIds.has(acc.id)) return false
      // 仅 REAL 账户参与现金叶子统计
      if (acc.accountKind !== 'REAL') return false
      // 只统计资产类账户（信贷账户如 CREDIT_CARD/HUABEI/BAITIAO/LOAN 视为负债，不计入可用资金）
      if (!assetTypes.has(acc.accountType)) return false
      return true
    })
    
    return leafAccounts
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
