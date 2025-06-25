import { defineStore } from 'pinia'
import { tiposProdutoApi, type TipoProdutoDTO } from '@/api'
import type { AxiosError } from 'axios'

interface ProductTypeState {
  productTypes: TipoProdutoDTO[]
  loading: boolean
  error: string | null
}

export const useProductTypeStore = defineStore('productType', {
  state: (): ProductTypeState => ({
    productTypes: [],
    loading: false,
    error: null
  }),

  actions: {
    async fetchProductTypes() {
      console.log('fetchProductTypes: iniciando busca...')
      this.loading = true
      this.error = null
      try {
        console.log('fetchProductTypes: chamando API...')
        const response = await tiposProdutoApi.buscarTodos()
        console.log('fetchProductTypes: resposta recebida:', response)
        this.productTypes = Array.isArray(response.data) ? response.data : [response.data]
        console.log('fetchProductTypes: tipos de produto carregados:', this.productTypes)
      } catch (error) {
        const axiosError = error as AxiosError
        this.error = axiosError.message
        console.error('fetchProductTypes: erro ao buscar tipos de produto:', error)
        console.error('fetchProductTypes: detalhes do erro:', {
          message: axiosError.message,
          response: axiosError.response,
          request: axiosError.request,
          config: axiosError.config
        })
      } finally {
        this.loading = false
      }
    },

    async createProductType(productType: TipoProdutoDTO) {
      this.loading = true
      this.error = null
      try {
        const response = await tiposProdutoApi.criar(productType)
        this.productTypes.push(response.data)
        return response.data
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      } finally {
        this.loading = false
      }
    },

    async updateProductType(id: number, productType: TipoProdutoDTO) {
      this.loading = true
      this.error = null
      try {
        const response = await tiposProdutoApi.atualizar(id, productType)
        const index = this.productTypes.findIndex(pt => pt.id === id)
        if (index !== -1) {
          this.productTypes[index] = response.data
        }
        return response.data
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      } finally {
        this.loading = false
      }
    },

    async deleteProductType(id: number) {
      this.loading = true
      this.error = null
      try {
        await tiposProdutoApi.excluir(id)
        this.productTypes = this.productTypes.filter(pt => pt.id !== id)
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      } finally {
        this.loading = false
      }
    },

    async getProductTypeById(id: number) {
      try {
        const response = await tiposProdutoApi.buscarPorId(id)
        return response.data
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      }
    }
  }
}) 