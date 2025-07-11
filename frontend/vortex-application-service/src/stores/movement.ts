import { defineStore } from 'pinia'
import { movimentosApi, type MovimentoEstoque } from '@/api'
import type { AxiosError } from 'axios'

interface MovementState {
  movements: MovimentoEstoque[]
  loading: boolean
  error: string | null
}

export const useMovementStore = defineStore('movement', {
  state: (): MovementState => ({
    movements: [],
    loading: false,
    error: null
  }),

  actions: {
    async fetchMovements() {
      this.loading = true
      this.error = null
      try {
        const response = await movimentosApi.findAllMovimentos()
        this.movements = Array.isArray(response.data) ? response.data : [response.data]
      } catch (error) {
        this.error = (error as AxiosError).message
        console.error('Erro ao buscar movimentos:', error)
      } finally {
        this.loading = false
      }
    },

    async createMovement(movement: MovimentoEstoque) {
      this.loading = true
      this.error = null
      try {
        const response = await movimentosApi.createMovimento(movement)
        this.movements.push(response.data)
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
        const index = this.movements.findIndex(m => m.id === id)
        if (index !== -1) {
          this.movements[index] = response.data
        }
        return response.data
      } catch (error) {
        this.error = (error as AxiosError).message
        throw error
      } finally {
        this.loading = false
      }
    },

    async deleteMovement(id: number) {
      this.loading = true
      this.error = null
      try {
        await movimentosApi.deleteMovimento(id)
        this.movements = this.movements.filter(m => m.id !== id)
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
  },

  getters: {
    getMovementsByProduct: (state) => (productId: number) => {
      return state.movements.filter(m => m.produtoId === productId)
    },
    
    getMovementsByType: (state) => (type: 'ENTRADA' | 'SAIDA') => {
      return state.movements.filter(m => m.tipoMovimentacao === type)
    }
  }
}) 