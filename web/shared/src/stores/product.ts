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
      const data = await productApi.getProducts(params)
      console.log('API returned data:', data)
      console.log('Data type:', Array.isArray(data), 'Length:', data?.length)
      
      // 处理字段名不一致的问题（后端可能返回isqdii，前端期望isQdii）
      // 同时确保所有必需字段都有值
      const processedData = (data || []).map((product: any) => ({
        ...product,
        isQdii: product.isQdii ?? product.isqdii ?? false,
        isActive: product.isActive ?? true,
      }))
      
      products.value = processedData
      console.log('Products stored:', products.value.length)
      console.log('First product:', products.value[0])
    } catch (error) {
      console.error('Failed to load products:', error)
      products.value = []
    } finally {
      loading.value = false
      console.log('Loading finished, products count:', products.value.length)
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
