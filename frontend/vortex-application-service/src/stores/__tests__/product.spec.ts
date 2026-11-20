import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { produtosApi, type ProdutoDTO } from '@/api'
import { useProductStore } from '@/stores/product'
import { okResponse, pagedResponse } from '@/__tests__/pagedResponses'

// Só o cliente da API é trocado: a store roda de verdade contra respostas no formato {content, page}
vi.mock('@/api', () => ({
  produtosApi: {
    findAllProdutos: vi.fn(),
    findProdutoById: vi.fn(),
    createProduto: vi.fn(),
    updateProduto: vi.fn(),
    deleteProduto: vi.fn(),
  },
}))

const api = vi.mocked(produtosApi)

const produto = (id: number, descricao = `Produto ${id}`): ProdutoDTO => ({
  id,
  descricao,
  valorFornecedor: 10,
  quantidadeEmEstoque: 5,
  tipoProdutoId: 1,
})

describe('product store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('asks the API for page 0 when the table shows page 1, with the server defaults', async () => {
    api.findAllProdutos.mockResolvedValue(pagedResponse([produto(1), produto(2)], { totalElements: 42 }))
    const store = useProductStore()

    await store.fetchProducts()

    // Sem texto e sem ordenação, q e sort ficam de fora da URL e valem os padrões do servidor
    expect(api.findAllProdutos).toHaveBeenCalledExactlyOnceWith(undefined, 0, 20, undefined)
    expect(store.items.map(p => p.id)).toEqual([1, 2])
    expect(store.totalItems).toBe(42)
    expect(store.page).toBe(1)
    expect(store.loading).toBe(false)
  })

  it('sends page, size, sort and the trimmed search text, and keeps them as the current page', async () => {
    api.findAllProdutos.mockResolvedValue(pagedResponse([produto(7)], { totalElements: 61, number: 2, size: 50 }))
    const store = useProductStore()

    await store.fetchProducts({
      page: 3,
      itemsPerPage: 50,
      sortBy: [{ key: 'valorFornecedor', order: 'desc' }, { key: 'descricao', order: 'asc' }],
      q: '  notebook ',
    })

    expect(api.findAllProdutos).toHaveBeenCalledExactlyOnceWith(
      'notebook',
      2,
      50,
      ['valorFornecedor,desc', 'descricao,asc'],
    )
    expect(store.page).toBe(3)
    expect(store.itemsPerPage).toBe(50)
    expect(store.q).toBe('  notebook ')
    expect(store.totalItems).toBe(61)

    // Sem opções, a próxima busca repete a página corrente
    await store.fetchProducts()
    expect(api.findAllProdutos).toHaveBeenLastCalledWith('notebook', 2, 50, ['valorFornecedor,desc', 'descricao,asc'])
  })

  it('keeps only the answer of the latest search when an older one arrives later', async () => {
    let answerFirst: (value: Awaited<ReturnType<typeof produtosApi.findAllProdutos>>) => void = () => undefined
    api.findAllProdutos
      .mockReturnValueOnce(new Promise(resolve => { answerFirst = resolve }))
      .mockResolvedValueOnce(pagedResponse([produto(2, 'note')], { totalElements: 1 }))
    const store = useProductStore()

    const first = store.fetchProducts({ q: 'n' })
    await store.fetchProducts({ q: 'note' })
    answerFirst(pagedResponse([produto(1), produto(3)], { totalElements: 2 }))
    await first

    expect(store.items.map(p => p.id)).toEqual([2])
    expect(store.totalItems).toBe(1)
    expect(store.loading).toBe(false)
  })

  it('records the error and keeps the last page when the search fails', async () => {
    api.findAllProdutos
      .mockResolvedValueOnce(pagedResponse([produto(1)]))
      .mockRejectedValueOnce(new Error('Request failed with status code 400'))
    const store = useProductStore()
    await store.fetchProducts()

    await store.fetchProducts({ sortBy: [{ key: 'inexistente', order: 'asc' }] })

    expect(store.error).toBe('Request failed with status code 400')
    expect(store.items.map(p => p.id)).toEqual([1])
    expect(store.loading).toBe(false)
  })

  it('reloads the current page from the server after creating and updating, instead of pushing locally', async () => {
    api.findAllProdutos.mockResolvedValue(pagedResponse([produto(1)], { totalElements: 21, number: 1 }))
    api.createProduto.mockResolvedValue(okResponse(produto(99), 201))
    api.updateProduto.mockResolvedValue(okResponse(produto(1, 'Novo nome')))
    const store = useProductStore()
    await store.fetchProducts({ page: 2, q: 'prod' })
    api.findAllProdutos.mockClear()

    await expect(store.createProduct(produto(0))).resolves.toEqual(produto(99))
    expect(api.findAllProdutos).toHaveBeenLastCalledWith('prod', 1, 20, undefined)

    await expect(store.updateProduct(1, produto(1, 'Novo nome'))).resolves.toEqual(produto(1, 'Novo nome'))
    expect(api.updateProduto).toHaveBeenCalledWith(1, produto(1, 'Novo nome'))
    expect(api.findAllProdutos).toHaveBeenCalledTimes(2)
    expect(api.findAllProdutos).toHaveBeenLastCalledWith('prod', 1, 20, undefined)
    // A lista é a do servidor: o criado não entrou na página corrente por push
    expect(store.items.map(p => p.id)).toEqual([1])
    expect(store.totalItems).toBe(21)
  })

  it('reloads the current page after deleting', async () => {
    api.findAllProdutos
      .mockResolvedValueOnce(pagedResponse([produto(1), produto(2)], { totalElements: 2 }))
      .mockResolvedValueOnce(pagedResponse([produto(2)], { totalElements: 1 }))
    api.deleteProduto.mockResolvedValue(okResponse(undefined, 204))
    const store = useProductStore()
    await store.fetchProducts()

    await store.deleteProduct(1)

    expect(api.deleteProduto).toHaveBeenCalledWith(1)
    expect(api.findAllProdutos).toHaveBeenCalledTimes(2)
    expect(api.findAllProdutos).toHaveBeenLastCalledWith(undefined, 0, 20, undefined)
    expect(store.items.map(p => p.id)).toEqual([2])
    expect(store.page).toBe(1)
  })

  it('goes back one page when deleting the only item of the last page', async () => {
    api.findAllProdutos
      .mockResolvedValueOnce(pagedResponse([produto(41)], { totalElements: 41, number: 2 }))
      // A página 3 (índice 2) ficou vazia: a API responde 200 com content vazio
      .mockResolvedValueOnce(pagedResponse<ProdutoDTO>([], { totalElements: 40, number: 2 }))
      .mockResolvedValueOnce(pagedResponse([produto(21), produto(22)], { totalElements: 40, number: 1 }))
    api.deleteProduto.mockResolvedValue(okResponse(undefined, 204))
    const store = useProductStore()
    await store.fetchProducts({ page: 3 })

    await store.deleteProduct(41)

    expect(api.findAllProdutos).toHaveBeenNthCalledWith(2, undefined, 2, 20, undefined)
    expect(api.findAllProdutos).toHaveBeenNthCalledWith(3, undefined, 1, 20, undefined)
    expect(store.page).toBe(2)
    expect(store.items.map(p => p.id)).toEqual([21, 22])
    expect(store.totalItems).toBe(40)
  })

  it('stays on page 1 when the delete empties it', async () => {
    api.findAllProdutos
      .mockResolvedValueOnce(pagedResponse([produto(1)]))
      .mockResolvedValueOnce(pagedResponse<ProdutoDTO>([], { totalElements: 0 }))
    api.deleteProduto.mockResolvedValue(okResponse(undefined, 204))
    const store = useProductStore()
    await store.fetchProducts()

    await store.deleteProduct(1)

    expect(api.findAllProdutos).toHaveBeenCalledTimes(2)
    expect(store.page).toBe(1)
    expect(store.items).toEqual([])
  })

  it('rethrows a failed delete without reloading', async () => {
    api.findAllProdutos.mockResolvedValue(pagedResponse([produto(1)]))
    api.deleteProduto.mockRejectedValue(new Error('Request failed with status code 400'))
    const store = useProductStore()
    await store.fetchProducts()

    await expect(store.deleteProduct(1)).rejects.toThrow('status code 400')

    expect(api.findAllProdutos).toHaveBeenCalledTimes(1)
    expect(store.error).toBe('Request failed with status code 400')
    expect(store.loading).toBe(false)
  })

  it('searches products for the autocomplete without touching the products page', async () => {
    api.findAllProdutos
      .mockResolvedValueOnce(pagedResponse([produto(1)], { totalElements: 30, number: 1, size: 10 }))
      .mockResolvedValueOnce(pagedResponse([produto(5, 'Notebook')], { totalElements: 1 }))
    const store = useProductStore()
    await store.fetchProducts({ page: 2, itemsPerPage: 10, q: 'abc' })

    const found = await store.searchProducts(' Note ')

    // Primeira página, 20 itens, ordenação padrão do servidor (descrição)
    expect(api.findAllProdutos).toHaveBeenLastCalledWith('Note', 0, 20)
    expect(found.map(p => p.descricao)).toEqual(['Notebook'])
    expect(store.items.map(p => p.id)).toEqual([1])
    expect(store.totalItems).toBe(30)
    expect(store.page).toBe(2)
    expect(store.itemsPerPage).toBe(10)
    expect(store.q).toBe('abc')
  })

  it('sends no q for an empty autocomplete search and returns an empty list when it fails', async () => {
    api.findAllProdutos
      .mockResolvedValueOnce(pagedResponse([produto(1), produto(2)]))
      .mockRejectedValueOnce(new Error('Network Error'))
    const store = useProductStore()

    await expect(store.searchProducts('')).resolves.toHaveLength(2)
    expect(api.findAllProdutos).toHaveBeenLastCalledWith(undefined, 0, 20)
    await expect(store.searchProducts('x')).resolves.toEqual([])
    expect(store.error).toBeNull()
  })
})
