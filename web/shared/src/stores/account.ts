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
   */
  const accountTree = computed(() => {
    return buildAccountTree(accounts.value)
  })

  /**
   * 现金叶子账户
   */
  const cashLeafAccounts = computed(() => {
    return getLeafAccounts(accountTree.value).filter(
      (acc) => acc.accountKind === 'REAL' && acc.accountType === 'CASH'
    )
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
