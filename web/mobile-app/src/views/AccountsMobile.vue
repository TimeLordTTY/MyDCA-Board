<template>
  <div class="accounts-page">
    <van-nav-bar title="账户管理" fixed placeholder />

    <van-pull-refresh v-model="refreshing" @refresh="loadData">
      <div class="page-container">
        <van-tabs v-model:active="typeFilter" type="card" shrink>
          <van-tab title="全部" name="ALL" />
          <van-tab title="资产类" name="ASSET" />
          <van-tab title="负债类" name="LIABILITY" />
        </van-tabs>

        <van-empty
          v-if="!loading && flatAccounts.length === 0"
          description="暂无账户"
          image="search"
        />

        <div v-else class="account-list">
          <div
            v-for="acc in filteredAccounts"
            :key="acc.id"
            class="account-card mobile-card"
            @click="showDetail(acc)"
          >
            <div class="account-main">
              <div class="account-name">{{ acc.accountName }}</div>
              <div class="account-type">{{ getAccountTypeLabel(acc.accountType) }}</div>
            </div>
            <div class="account-meta">
              <div class="account-balance">
                <span class="label">余额</span>
                <span class="value">{{ formatCurrency(acc.balance) }}</span>
              </div>
              <div class="account-balance">
                <span class="label">占用</span>
                <span class="value">{{ formatCurrency(acc.reservedAmount) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </van-pull-refresh>

    <van-popup
      v-model:show="showDetailDialog"
      position="bottom"
      :style="{ height: '70%' }"
      round
      closeable
    >
      <div v-if="current" class="detail-popup">
        <h3 class="popup-title">账户详情</h3>
        <van-cell-group inset>
          <van-cell title="名称" :value="current.accountName" />
          <van-cell title="编号" :value="current.accountCode" />
          <van-cell title="账户性质" :value="current.accountKind === 'REAL' ? '现实账户' : '虚拟科目'" />
          <van-cell title="账户类型" :value="getAccountTypeLabel(current.accountType)" />
          <van-cell title="币种" :value="current.currency" />
          <van-cell title="所有者类型" :value="current.ownerType === 'PERSONAL' ? '个人' : '家庭'" />
          <van-cell title="余额" :value="formatCurrency(current.balance)" />
          <van-cell title="占用金额" :value="formatCurrency(current.reservedAmount)" />
          <van-cell title="初始余额" :value="formatCurrency(current.initialBalance)" />
          <van-cell
            v-if="current.fundUsage"
            title="资金用途"
            :value="getFundUsageLabel(current.fundUsage)"
          />
          <van-cell
            v-if="current.note"
            title="备注"
            :value="current.note"
          />
        </van-cell-group>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { accountApi, type Account, formatCurrency, getAccountTypeLabel, getFundUsageLabel } from '@wealth-hub/shared'
import { showFailToast } from 'vant'

const loading = ref(false)
const refreshing = ref(false)
const accounts = ref<Account[]>([])
const typeFilter = ref<'ALL' | 'ASSET' | 'LIABILITY'>('ALL')

const showDetailDialog = ref(false)
const current = ref<Account | null>(null)

async function loadData() {
  try {
    loading.value = true
    const data = await accountApi.getAccounts()
    accounts.value = data
  } catch (e: any) {
    showFailToast(e.message || '加载账户失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

// 将账户树拍平成叶子账户列表
const flatAccounts = computed<Account[]>(() => {
  const result: Account[] = []

  function traverse(list: Account[]) {
    for (const acc of list) {
      if (acc.children && acc.children.length > 0) {
        traverse(acc.children)
      } else {
        result.push(acc)
      }
    }
  }

  traverse(accounts.value)
  return result
})

const filteredAccounts = computed(() => {
  return flatAccounts.value.filter(acc => {
    if (typeFilter.value === 'ALL') return true
    const isLiability =
      acc.accountType === 'CREDIT_CARD' ||
      acc.accountType === 'HUABEI' ||
      acc.accountType === 'BAITIAO' ||
      acc.accountType === 'LOAN'
    return typeFilter.value === 'LIABILITY' ? isLiability : !isLiability
  })
})

function showDetail(acc: Account) {
  current.value = acc
  showDetailDialog.value = true
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.account-list {
  padding: 8px 12px 16px;
}
.account-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.account-main {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
}
.account-name {
  font-size: var(--fs16);
  font-weight: 600;
}
.account-type {
  font-size: var(--fs12);
  color: var(--muted);
}
.account-meta {
  display: flex;
  gap: 16px;
  font-size: var(--fs12);
  color: var(--muted);
}
.account-balance .label {
  margin-right: 4px;
}
.account-balance .value {
  font-weight: 500;
}
.detail-popup {
  padding: 16px;
}
.popup-title {
  margin: 0 0 8px 0;
  font-size: var(--fs18);
}
</style>

