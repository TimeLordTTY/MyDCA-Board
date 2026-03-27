<template>
  <div class="products-page">
    <van-nav-bar title="产品管理" fixed placeholder />

    <van-pull-refresh v-model="refreshing" @refresh="loadData">
      <div class="page-container">
        <div class="search-bar">
          <van-search
            v-model="keyword"
            placeholder="搜索名称或代码"
            shape="round"
            clearable
            @search="onSearch"
            @clear="onSearch"
          />
        </div>

        <van-tabs v-model:active="channelFilter" type="card" shrink>
          <van-tab title="全部" name="ALL" />
          <van-tab title="场内" name="EXCHANGE" />
          <van-tab title="场外" name="OTC" />
        </van-tabs>

        <van-empty
          v-if="!loading && filteredProducts.length === 0"
          description="暂无产品"
          image="search"
        />

        <div v-else class="product-list">
          <div
            v-for="p in filteredProducts"
            :key="p.id"
            class="product-card mobile-card"
            @click="showDetail(p)"
          >
            <div class="product-main">
              <div class="product-name">{{ p.productName }}</div>
              <div class="product-code">{{ p.productCode }}</div>
            </div>
            <div class="product-meta">
              <div class="tag-row">
                <van-tag plain type="primary" size="small">
                  {{ getAssetTypeLabel(p.assetType) }}
                </van-tag>
                <van-tag plain size="small">
                  {{ getChannelLabel(p.channel) }}/{{ getMarketLabel(p.market) }}
                </van-tag>
              </div>
              <div class="fee-row">
                <span class="fee-label">买入费率</span>
                <span class="fee-value">{{ formatPercent(p.buyFeeRate) }}</span>
                <span class="fee-label">卖出费率</span>
                <span class="fee-value">{{ formatPercent(p.sellFeeRate) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </van-pull-refresh>

    <van-popup
      v-model:show="showDetailDialog"
      position="bottom"
      :style="{ height: '75%' }"
      round
      closeable
    >
      <div v-if="current" class="detail-popup">
        <h3 class="popup-title">产品详情</h3>
        <van-cell-group inset>
          <van-cell title="名称" :value="current.productName" />
          <van-cell title="代码" :value="current.productCode" />
          <van-cell title="资产类型" :value="getAssetTypeLabel(current.assetType)" />
          <van-cell title="渠道" :value="getChannelLabel(current.channel)" />
          <van-cell title="市场" :value="getMarketLabel(current.market)" />
          <van-cell title="币种" :value="current.currency" />
          <van-cell title="QDII" :value="current.isQdii || current.isqdii ? '是' : '否'" />
          <van-cell v-if="current.trackIndex" title="跟踪指数" :value="current.trackIndex" />
          <van-cell title="买入费率" :value="formatPercent(current.buyFeeRate)" />
          <van-cell title="卖出费率" :value="formatPercent(current.sellFeeRate)" />
          <van-cell title="买入确认+天数" :value="current.buyConfirmOffset" />
          <van-cell title="卖出确认+天数" :value="current.sellConfirmOffset" />
          <van-cell title="交易截止时间" :value="current.cutoffTime" />
          <van-cell v-if="current.dataSource" title="数据源" :value="current.dataSource" />
          <van-cell title="状态" :value="current.isActive ? '启用' : '停用'" />
          <van-cell v-if="current.note" title="备注" :value="current.note" />
        </van-cell-group>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { productApi, type ProductMaster, getAssetTypeLabel, getChannelLabel, getMarketLabel } from '@wealth-hub/shared'
import { showFailToast } from 'vant'

const loading = ref(false)
const refreshing = ref(false)
const products = ref<ProductMaster[]>([])
const keyword = ref('')
const channelFilter = ref<'ALL' | 'EXCHANGE' | 'OTC'>('ALL')

const showDetailDialog = ref(false)
const current = ref<ProductMaster | null>(null)

async function loadData() {
  try {
    loading.value = true
    const data = await productApi.getProducts()
    products.value = data
  } catch (e: any) {
    showFailToast(e.message || '加载产品失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function onSearch() {
  // 只是触发 computed 重新计算，这里不需要额外逻辑
}

const filteredProducts = computed(() => {
  let list = products.value

  if (channelFilter.value !== 'ALL') {
    list = list.filter(p => p.channel === channelFilter.value)
  }

  if (keyword.value.trim()) {
    const kw = keyword.value.trim().toLowerCase()
    list = list.filter(
      p =>
        p.productName.toLowerCase().includes(kw) ||
        p.productCode.toLowerCase().includes(kw)
    )
  }

  // 简单按 sortOrder / id 排序
  return [...list].sort((a, b) => {
    const sa = a.sortOrder ?? 999999
    const sb = b.sortOrder ?? 999999
    if (sa !== sb) return sa - sb
    return a.id - b.id
  })
})

function formatPercent(v: number | undefined | null): string {
  if (v == null) return '-'
  return (v * 100).toFixed(2) + '%'
}

function showDetail(p: ProductMaster) {
  current.value = p
  showDetailDialog.value = true
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.products-page .search-bar {
  padding: 12px 12px 4px;
}
.product-list {
  padding: 8px 12px 16px;
}
.product-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.product-main {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
}
.product-name {
  font-size: var(--fs16);
  font-weight: 600;
  color: var(--text);
}
.product-code {
  font-size: var(--fs12);
  color: var(--muted);
}
.product-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.tag-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.fee-row {
  font-size: var(--fs12);
  color: var(--muted);
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.fee-label {
  opacity: 0.9;
}
.fee-value {
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

