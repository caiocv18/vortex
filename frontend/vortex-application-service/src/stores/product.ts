import { defineStore } from 'pinia'
import { produtosApi, type ProdutoDTO } from '@/api'
import type { AxiosError } from 'axios'
import {
  AUTOCOMPLETE_SIZE,
  DEFAULT_ITEMS_PER_PAGE,
  toApiPage,
  toQueryText,
  toSortParam,
  type SortItem,
} from './pagination'

// Página corrente da tela de produtos, como a tabela a mostra (page começa em 1)
interface ProductState {
  items: ProdutoDTO[]
  totalItems: number
  page: number
  itemsPerPage: number
  sortBy: SortItem[]
  q: string
  loading: boolean
  error: string | null
}

export interface ProductPageOptions {
  page?: number
  itemsPerPage?: number
  sortBy?: SortItem[]
  q?: string
}

// Só a resposta da última busca entra na tela: digitando rápido, uma resposta atrasada não sobrescreve a nova
let latestFetch = 0

export const useProductStore = defineStore('product', {
  state: (): ProductState => ({
    items: [],
    totalItems: 0,
    page: 1,
    itemsPerPage: DEFAULT_ITEMS_PER_PAGE,
    sortBy: [],
    q: '',
    loading: false,
    error: null
  }),

  actions: {
    // Busca uma página no servidor; as opções omitidas mantêm as da página corrente
    async fetchProducts(options: ProductPageOptions = {}) {
      if (options.page !== undefined) this.page = options.page
      if (options.itemsPerPage !== undefined) this.itemsPerPage = options.itemsPerPage
      if (options.sortBy !== undefined) this.sortBy = options.sortBy
      if (options.q !== undefined) this.q = options.q

      const request = ++latestFetch
      this.loading = true
      this.error = null
      try {
        const response = await produtosApi.findAllProdutos(
          toQueryText(this.q),
          toApiPage(this.page),
          this.itemsPerPage,
          toSortParam(this.sortBy)
        )
        if (request !== latestFetch) return
        this.items = response.data.content
        this.totalItems = response.data.page.totalElements
      } catch (error) {
        if (request !== latestFetch) return
        this.error = (error as AxiosError).message
        console.error('Erro ao buscar produtos:', error)
      } finally {
        if (request === latestFetch) this.loading = false
      }
    },

    // Busca do autocomplete (formulário de movimento): devolve a lista sem mexer na página da tela de produtos
    async searchProducts(q: string): Promise<ProdutoDTO[]> {
      try {
        const response = await produtosApi.findAllProdutos(toQueryText(q), 0, AUTOCOMPLETE_SIZE)
        return response.data.content
      } catch (error) {
        console.error('Erro ao buscar produtos:', error)
        return []
      }
    },

    // Depois de criar, a página corrente é recarregada do servidor: um push local quebraria a contagem
    async createProduct(product: ProdutoDTO) {
      this.loading = true
      this.error = null
      try {
        const response = await produtosApi.createProduto(product)
        await this.fetchProducts()
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
        const response = await produtosApi.updateProduto(id, product)
        await this.fetchProducts()
        return response.data
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      } finally {
        this.loading = false
      }
    },

    // Se a exclusão esvaziou a última página, volta uma página
    async deleteProduct(id: number) {
      this.loading = true
      this.error = null
      try {
        await produtosApi.deleteProduto(id)
        await this.fetchProducts()
        if (this.items.length === 0 && this.page > 1) {
          await this.fetchProducts({ page: this.page - 1 })
        }
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      } finally {
        this.loading = false
      }
    },

    async getProductById(id: number) {
      try {
        const response = await produtosApi.findProdutoById(id)
        return response.data
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      }
    }
  }
})
