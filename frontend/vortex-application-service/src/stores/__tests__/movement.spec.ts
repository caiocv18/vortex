import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { movimentosApi, type MovimentoEstoque } from '@/api'
import { useMovementStore } from '@/stores/movement'
import { okResponse, pagedResponse } from '@/__tests__/pagedResponses'

// Só o cliente da API é trocado: a store roda de verdade contra respostas no formato {content, page}
vi.mock('@/api', () => ({
  movimentosApi: {
    findAllMovimentos: vi.fn(),
    findMovimentoById: vi.fn(),
    createMovimento: vi.fn(),
    updateMovimento: vi.fn(),
    deleteMovimento: vi.fn(),
  },
}))

const api = vi.mocked(movimentosApi)

const movimento = (id: number, tipoMovimentacao: 'ENTRADA' | 'SAIDA' = 'ENTRADA'): MovimentoEstoque => ({
  id,
  produtoId: 1,
  produtoDescricao: 'Notebook',
  tipoMovimentacao,
  quantidadeMovimentada: 2,
})

describe('movement store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('asks the API for page 0 when the table shows page 1, with no filters and the server sort', async () => {
    api.findAllMovimentos.mockResolvedValue(pagedResponse([movimento(2), movimento(1)], { totalElements: 35 }))
    const store = useMovementStore()

    await store.fetchMovements()

    expect(api.findAllMovimentos).toHaveBeenCalledExactlyOnceWith(undefined, undefined, 0, 20, undefined)
    expect(store.items.map(m => m.id)).toEqual([2, 1])
    // A descrição do produto vem pronta na linha
    expect(store.items[0]!.produtoDescricao).toBe('Notebook')
    expect(store.totalItems).toBe(35)
    expect(store.loading).toBe(false)
  })

  it('sends q, tipo, page, size and sort in the order of the API', async () => {
    api.findAllMovimentos.mockResolvedValue(pagedResponse([movimento(9, 'SAIDA')], { totalElements: 11, number: 1, size: 10 }))
    const store = useMovementStore()

    await store.fetchMovements({
      page: 2,
      itemsPerPage: 10,
      sortBy: [{ key: 'quantidadeMovimentada', order: 'asc' }],
      q: ' note ',
      tipo: 'SAIDA',
    })

    expect(api.findAllMovimentos).toHaveBeenCalledExactlyOnceWith('note', 'SAIDA', 1, 10, ['quantidadeMovimentada,asc'])
    expect(store.page).toBe(2)
    expect(store.tipo).toBe('SAIDA')
    expect(store.totalItems).toBe(11)
  })

  it('keeps the type filter until it is set back to all with null', async () => {
    api.findAllMovimentos.mockResolvedValue(pagedResponse([movimento(1)]))
    const store = useMovementStore()

    await store.fetchMovements({ tipo: 'ENTRADA' })
    await store.fetchMovements({ page: 1 })
    expect(api.findAllMovimentos).toHaveBeenLastCalledWith(undefined, 'ENTRADA', 0, 20, undefined)

    await store.fetchMovements({ tipo: null })
    expect(api.findAllMovimentos).toHaveBeenLastCalledWith(undefined, undefined, 0, 20, undefined)
    expect(store.tipo).toBeUndefined()
  })

  it('reloads the current page from the server after creating and updating, instead of pushing locally', async () => {
    api.findAllMovimentos.mockResolvedValue(pagedResponse([movimento(1)], { totalElements: 3 }))
    api.createMovimento.mockResolvedValue(okResponse(movimento(50), 201))
    api.updateMovimento.mockResolvedValue(okResponse(movimento(1, 'SAIDA')))
    const store = useMovementStore()
    await store.fetchMovements({ tipo: 'ENTRADA', q: 'note' })
    api.findAllMovimentos.mockClear()

    await expect(store.createMovement(movimento(0))).resolves.toEqual(movimento(50))
    await expect(store.updateMovement(1, movimento(1, 'SAIDA'))).resolves.toEqual(movimento(1, 'SAIDA'))

    expect(api.updateMovimento).toHaveBeenCalledWith(1, movimento(1, 'SAIDA'))
    expect(api.findAllMovimentos).toHaveBeenCalledTimes(2)
    expect(api.findAllMovimentos).toHaveBeenNthCalledWith(1, 'note', 'ENTRADA', 0, 20, undefined)
    expect(api.findAllMovimentos).toHaveBeenNthCalledWith(2, 'note', 'ENTRADA', 0, 20, undefined)
    expect(store.items.map(m => m.id)).toEqual([1])
    expect(store.totalItems).toBe(3)
  })

  it('reloads the current page after deleting', async () => {
    api.findAllMovimentos
      .mockResolvedValueOnce(pagedResponse([movimento(1), movimento(2)], { totalElements: 22, number: 1 }))
      .mockResolvedValueOnce(pagedResponse([movimento(2)], { totalElements: 21, number: 1 }))
    api.deleteMovimento.mockResolvedValue(okResponse(undefined, 204))
    const store = useMovementStore()
    await store.fetchMovements({ page: 2 })

    await store.deleteMovement(1)

    expect(api.deleteMovimento).toHaveBeenCalledWith(1)
    expect(api.findAllMovimentos).toHaveBeenCalledTimes(2)
    expect(api.findAllMovimentos).toHaveBeenLastCalledWith(undefined, undefined, 1, 20, undefined)
    expect(store.page).toBe(2)
    expect(store.items.map(m => m.id)).toEqual([2])
  })

  it('goes back one page when deleting the only item of the last page', async () => {
    api.findAllMovimentos
      .mockResolvedValueOnce(pagedResponse([movimento(21)], { totalElements: 21, number: 1 }))
      .mockResolvedValueOnce(pagedResponse<MovimentoEstoque>([], { totalElements: 20, number: 1 }))
      .mockResolvedValueOnce(pagedResponse([movimento(1), movimento(2)], { totalElements: 20 }))
    api.deleteMovimento.mockResolvedValue(okResponse(undefined, 204))
    const store = useMovementStore()
    await store.fetchMovements({ page: 2, tipo: 'SAIDA' })

    await store.deleteMovement(21)

    expect(api.findAllMovimentos).toHaveBeenNthCalledWith(2, undefined, 'SAIDA', 1, 20, undefined)
    expect(api.findAllMovimentos).toHaveBeenNthCalledWith(3, undefined, 'SAIDA', 0, 20, undefined)
    expect(store.page).toBe(1)
    expect(store.items.map(m => m.id)).toEqual([1, 2])
    expect(store.loading).toBe(false)
  })

  it('records the error of a rejected filter and keeps the last page', async () => {
    api.findAllMovimentos
      .mockResolvedValueOnce(pagedResponse([movimento(1)]))
      .mockRejectedValueOnce(new Error('Request failed with status code 400'))
    const store = useMovementStore()
    await store.fetchMovements()

    await store.fetchMovements({ sortBy: [{ key: 'produto', order: 'desc' }] })

    expect(api.findAllMovimentos).toHaveBeenLastCalledWith(undefined, undefined, 0, 20, ['produto,desc'])
    expect(store.error).toBe('Request failed with status code 400')
    expect(store.items.map(m => m.id)).toEqual([1])
    expect(store.loading).toBe(false)
  })
})
