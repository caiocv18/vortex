import { defineStore } from 'pinia'
import { movimentosApi, type FindAllMovimentosTipoEnum, type MovimentoEstoque } from '@/api'
import type { AxiosError } from 'axios'
import { DEFAULT_ITEMS_PER_PAGE, toApiPage, toQueryText, toSortParam, type SortItem } from './pagination'

// Página corrente da tela de movimentos, como a tabela a mostra (page começa em 1).
// q busca pela descrição do produto; tipo ausente traz entradas e saídas.
interface MovementState {
  items: MovimentoEstoque[]
  totalItems: number
  page: number
  itemsPerPage: number
  sortBy: SortItem[]
  q: string
  tipo: FindAllMovimentosTipoEnum | undefined
  loading: boolean
  error: string | null
}

export interface MovementPageOptions {
  page?: number
  itemsPerPage?: number
  sortBy?: SortItem[]
  q?: string
  // null volta para "todos" (undefined só mantém o filtro atual)
  tipo?: FindAllMovimentosTipoEnum | null
}

// Só a resposta da última busca entra na tela: digitando rápido, uma resposta atrasada não sobrescreve a nova
let latestFetch = 0

export const useMovementStore = defineStore('movement', {
  state: (): MovementState => ({
    items: [],
    totalItems: 0,
    page: 1,
    itemsPerPage: DEFAULT_ITEMS_PER_PAGE,
    sortBy: [],
    q: '',
    tipo: undefined,
    loading: false,
    error: null
  }),

  actions: {
    // Busca uma página no servidor; as opções omitidas mantêm as da página corrente
    async fetchMovements(options: MovementPageOptions = {}) {
      if (options.page !== undefined) this.page = options.page
      if (options.itemsPerPage !== undefined) this.itemsPerPage = options.itemsPerPage
      if (options.sortBy !== undefined) this.sortBy = options.sortBy
      if (options.q !== undefined) this.q = options.q
      if (options.tipo !== undefined) this.tipo = options.tipo ?? undefined

      const request = ++latestFetch
      this.loading = true
      this.error = null
      try {
        const response = await movimentosApi.findAllMovimentos(
          toQueryText(this.q),
          this.tipo,
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
        console.error('Erro ao buscar movimentos:', error)
      } finally {
        if (request === latestFetch) this.loading = false
      }
    },

    // Depois de criar, a página corrente é recarregada do servidor: um push local quebraria a contagem
    async createMovement(movement: MovimentoEstoque) {
      this.loading = true
      this.error = null
      try {
        const response = await movimentosApi.createMovimento(movement)
        await this.fetchMovements()
        return response.data
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      } finally {
        this.loading = false
      }
    },

    async updateMovement(id: number, movement: MovimentoEstoque) {
      this.loading = true
      this.error = null
      try {
        const response = await movimentosApi.updateMovimento(id, movement)
        await this.fetchMovements()
        return response.data
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      } finally {
        this.loading = false
      }
    },

    // Se a exclusão esvaziou a última página, volta uma página
    async deleteMovement(id: number) {
      this.loading = true
      this.error = null
      try {
        await movimentosApi.deleteMovimento(id)
        await this.fetchMovements()
        if (this.items.length === 0 && this.page > 1) {
          await this.fetchMovements({ page: this.page - 1 })
        }
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      } finally {
        this.loading = false
      }
    },

    async getMovementById(id: number) {
      try {
        const response = await movimentosApi.findMovimentoById(id)
        return response.data
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      }
    }
  }
})
