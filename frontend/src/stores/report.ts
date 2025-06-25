import { defineStore } from 'pinia'
import { relatoriosApi, type ProdutoPorTipoDTO, type LucroPorProdutoDTO } from '@/api'
import type { AxiosError } from 'axios'

interface ReportState {
  productsByType: ProdutoPorTipoDTO[]
  profitByProduct: LucroPorProdutoDTO[]
  loading: boolean
  error: string | null
}

export const useReportStore = defineStore('report', {
  state: (): ReportState => ({
    productsByType: [],
    profitByProduct: [],
    loading: false,
    error: null
  }),

  actions: {
    async fetchProductsByType(typeId: number) {
      this.loading = true
      this.error = null
      try {
        const response = await relatoriosApi.gerarRelatorioProdutosPorTipo(typeId)
        this.productsByType = Array.isArray(response.data) ? response.data : [response.data]
        return this.productsByType
      } catch (error) {
        this.error = (error as AxiosError).message
        console.error('Erro ao buscar relatório de produtos por tipo:', error)
        throw error
      } finally {
        this.loading = false
      }
    },

    async fetchProfitByProduct() {
      this.loading = true
      this.error = null
      try {
        const response = await relatoriosApi.gerarRelatorioLucroPorProduto()
        this.profitByProduct = Array.isArray(response.data) ? response.data : [response.data]
        return this.profitByProduct
      } catch (error) {
        this.error = (error as AxiosError).message
        console.error('Erro ao buscar relatório de lucro por produto:', error)
        throw error
      } finally {
        this.loading = false
      }
    }
  },

  getters: {
    totalProfit: (state) => {
      return state.profitByProduct.reduce((total, item) => total + (item.lucroTotal || 0), 0)
    },
    
    totalSoldUnits: (state) => {
      return state.profitByProduct.reduce((total, item) => total + (item.totalUnidadesVendidas || 0), 0)
    }
  }
}) 