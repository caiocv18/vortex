import { defineStore } from 'pinia'
import { produtosApi, type ProdutoDTO } from '@/api'
import type { AxiosError } from 'axios'

interface ProductState {
  products: ProdutoDTO[]
  loading: boolean
  error: string | null
}

export const useProductStore = defineStore('product', {
  state: (): ProductState => ({
    products: [],
    loading: false,
    error: null
  }),

  actions: {
    async fetchProducts() {
      this.loading = true
      this.error = null
      try {
        const response = await produtosApi.buscarTodos1()
        this.products = Array.isArray(response.data) ? response.data : [response.data]
      } catch (error) {
        this.error = (error as AxiosError).message
        console.error('Erro ao buscar produtos:', error)
      } finally {
        this.loading = false
      }
    },

    async createProduct(product: ProdutoDTO) {
      this.loading = true
      this.error = null
      try {
        const response = await produtosApi.criar1(product)
        this.products.push(response.data)
        return response.data
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      } finally {
        this.loading = false
      }
    },

    async updateProduct(id: number, product: ProdutoDTO) {
      this.loading = true
      this.error = null
      try {
        const response = await produtosApi.atualizar1(id, product)
        const index = this.products.findIndex(p => p.id === id)
        if (index !== -1) {
          this.products[index] = response.data
        }
        return response.data
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      } finally {
        this.loading = false
      }
    },

    async deleteProduct(id: number) {
      this.loading = true
      this.error = null
      try {
        await produtosApi.excluir1(id)
        this.products = this.products.filter(p => p.id !== id)
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      } finally {
        this.loading = false
      }
    },

    async getProductById(id: number) {
      try {
        const response = await produtosApi.buscarPorId1(id)
        return response.data
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      }
    }
  },

  getters: {
    getProductsByType: (state) => (typeId: number) => {
      return state.products.filter(p => p.tipoProdutoId === typeId)
    }
  }
}) 