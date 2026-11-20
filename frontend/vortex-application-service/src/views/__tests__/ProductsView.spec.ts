import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createVuetify } from 'vuetify'
import { produtosApi, tiposProdutoApi, type ProdutoDTO, type TipoProdutoDTO } from '@/api'
import ProductsView from '@/views/ProductsView.vue'
import { okResponse, pagedResponse } from '@/__tests__/pagedResponses'

// A tela monta de verdade (store, v-data-table-server e o footer do Vuetify); só o cliente da API
// é trocado. Os cliques vão no DOM da tabela: um spec que chamasse loadItems direto não pegaria a
// tabela gravando a store antes de emitir update:options (o defeito do v-model).
vi.mock('@/api', () => ({
  produtosApi: {
    findAllProdutos: vi.fn(),
    findProdutoById: vi.fn(),
    createProduto: vi.fn(),
    updateProduto: vi.fn(),
    deleteProduto: vi.fn(),
  },
  tiposProdutoApi: {
    findAllTiposProduto: vi.fn(),
  },
}))

const api = vi.mocked(produtosApi)
const typesApi = vi.mocked(tiposProdutoApi)

const produto = (id: number): ProdutoDTO => ({
  id,
  descricao: `Produto ${id}`,
  valorFornecedor: 10,
  tipoProdutoId: 1,
  quantidadeEmEstoque: 5,
})

// 45 produtos com 20 por página: 3 páginas, então o botão de próxima página fica habilitado
const firstPage = () => pagedResponse(Array.from({ length: 20 }, (_, i) => produto(i + 1)), { totalElements: 45 })

let wrapper: VueWrapper | undefined

async function mountView() {
  wrapper = mount(ProductsView, {
    global: { plugins: [createPinia(), createVuetify()] },
  })
  await flushPromises()
  return wrapper
}

// Botão do VPagination do footer; o aria-label vem do locale padrão do Vuetify (en: dataFooter.nextPage)
const nextPageButton = () => wrapper!.find('button[aria-label="Next page"]')
const header = (title: string) => wrapper!.findAll('th').find(th => th.text().includes(title))!
const searchInput = () => wrapper!.find('.v-card-title input')

describe('ProductsView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.findAllProdutos.mockResolvedValue(firstPage())
    typesApi.findAllTiposProduto.mockResolvedValue(okResponse({ id: 1, nome: 'Eletrônicos' } as TipoProdutoDTO))
    vi.spyOn(console, 'log').mockImplementation(() => undefined)
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('loads page 0 with 20 items and the server sort when it mounts', async () => {
    await mountView()

    // Sem sortBy na store, o sort não vai na URL e o servidor usa o padrão dele
    expect(api.findAllProdutos).toHaveBeenCalledExactlyOnceWith(undefined, 0, 20, undefined)
    expect(wrapper!.find('tbody').text()).toContain('Produto 1')
  })

  it('asks the server for page 1 when the next page button of the footer is clicked', async () => {
    await mountView()
    expect(nextPageButton().exists()).toBe(true)
    expect(nextPageButton().attributes('disabled')).toBeUndefined()

    await nextPageButton().trigger('click')
    await flushPromises()

    expect(api.findAllProdutos).toHaveBeenCalledTimes(2)
    expect(api.findAllProdutos).toHaveBeenLastCalledWith(undefined, 1, 20, undefined)
  })

  it('sorts by descricao ascending from the header and goes back to the first page', async () => {
    await mountView()
    await nextPageButton().trigger('click')
    await flushPromises()

    await header('Descrição').trigger('click')
    await flushPromises()

    expect(api.findAllProdutos).toHaveBeenCalledTimes(3)
    expect(api.findAllProdutos).toHaveBeenLastCalledWith(undefined, 0, 20, ['descricao,asc'])
  })

  it('sends the search text after the debounce, on the first page', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    await mountView()
    await nextPageButton().trigger('click')
    await flushPromises()
    expect(api.findAllProdutos).toHaveBeenCalledTimes(2)

    await searchInput().setValue('note')
    // Antes dos 300 ms nada sai: uma requisição quando o usuário para de digitar
    await vi.advanceTimersByTimeAsync(299)
    expect(api.findAllProdutos).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(1)
    await flushPromises()

    expect(api.findAllProdutos).toHaveBeenCalledTimes(3)
    expect(api.findAllProdutos).toHaveBeenLastCalledWith('note', 0, 20, undefined)
  })
})
