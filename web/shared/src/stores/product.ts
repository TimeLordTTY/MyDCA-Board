/**
 * 产品Store
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { productApi } from '../api'
import type { ProductMaster, ProductQueryParams } from '../types'

export const useProductStore = defineStore('product', () => {
  const products = ref<ProductMaster[]>([])
  const loading = ref(false)

  /**
   * 获取产品列表
   */
  async function fetchProducts(params?: ProductQueryParams) {
    loading.value = true
    try {
      products.value = await productApi.getProducts(params)
    } finally {
      loading.value = false
    }
  }

  /**
   * 获取产品详情
   */
  async function fetchProduct(id: number) {
    return await productApi.getProduct(id)
  }

  /**
   * 创建产品
   */
  async function createProduct(data: Partial<ProductMaster>) {
    const newProduct = await productApi.createProduct(data)
    await fetchProducts() // 刷新列表
    return newProduct
  }

  /**
   * 更新产品
   */
  async function updateProduct(id: number, data: Partial<ProductMaster>) {
    const updatedProduct = await productApi.updateProduct(id, data)
    await fetchProducts() // 刷新列表
    return updatedProduct
  }

  return {
    products,
    loading,
    fetchProducts,
    fetchProduct,
    createProduct,
    updateProduct,
  }
})
