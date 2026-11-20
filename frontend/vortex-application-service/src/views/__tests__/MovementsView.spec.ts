import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createVuetify } from 'vuetify'
import { movimentosApi, produtosApi, type MovimentoEstoque, type ProdutoDTO } from '@/api'
import MovementsView from '@/views/MovementsView.vue'
import { okResponse, pagedResponse } from '@/__tests__/pagedResponses'

// A tela monta de verdade (stores, v-data-table-server, filtro e diálogo); só o cliente da API é
// trocado. Paginação e filtro vão pelo DOM, como o usuário faz.
vi.mock('@/api', () => ({
  movimentosApi: {
    findAllMovimentos: vi.fn(),
    findMovimentoById: vi.fn(),
    createMovimento: vi.fn(),
    updateMovimento: vi.fn(),
    deleteMovimento: vi.fn(),
  },
  produtosApi: {
    findAllProdutos: vi.fn(),
    findProdutoById: vi.fn(),
  },
}))

const api = vi.mocked(movimentosApi)
const productsApi = vi.mocked(produtosApi)

const movimento = (id: number, produtoId = 7): MovimentoEstoque => ({
  id,
  produtoId,
  produtoDescricao: 'Notebook Dell',
  tipoMovimentacao: 'ENTRADA',
  quantidadeMovimentada: 2,
})

const produto = (id: number, descricao: string): ProdutoDTO => ({
  id,
  descricao,
  valorFornecedor: 100,
  tipoProdutoId: 1,
  quantidadeEmEstoque: 3,
})

// 45 movimentos com 20 por página: 3 páginas, então o botão de próxima página fica habilitado
const firstPage = () => pagedResponse(Array.from({ length: 20 }, (_, i) => movimento(i + 1)), { totalElements: 45 })

let wrapper: VueWrapper | undefined

async function mountView() {
  wrapper = mount(MovementsView, {
    global: { plugins: [createPinia(), createVuetify()] },
  })
  await flushPromises()
  return wrapper
}

// Botão do VPagination do footer; o aria-label vem do locale padrão do Vuetify (en: dataFooter.nextPage)
const nextPageButton = () => wrapper!.find('button[aria-label="Next page"]')
const filterButton = (label: string) => wrapper!.findAll('.v-btn-toggle button').find(b => b.text() === label)!

describe('MovementsView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.findAllMovimentos.mockResolvedValue(firstPage())
    vi.spyOn(console, 'log').mockImplementation(() => undefined)
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.restoreAllMocks()
  })

  it('loads page 0 with 20 items, no type filter and the server sort when it mounts', async () => {
    await mountView()

    expect(api.findAllMovimentos).toHaveBeenCalledExactlyOnceWith(undefined, undefined, 0, 20, undefined)
  })

  it('shows the product description that comes in the row', async () => {
    await mountView()

    // A coluna Produto lê produtoDescricao da linha, sem buscar o produto
    expect(wrapper!.find('tbody').text()).toContain('Notebook Dell')
    expect(productsApi.findProdutoById).not.toHaveBeenCalled()
  })

  it('asks the server for page 1 when the next page button of the footer is clicked', async () => {
    await mountView()
    expect(nextPageButton().exists()).toBe(true)

    await nextPageButton().trigger('click')
    await flushPromises()

    expect(api.findAllMovimentos).toHaveBeenCalledTimes(2)
    expect(api.findAllMovimentos).toHaveBeenLastCalledWith(undefined, undefined, 1, 20, undefined)
  })

  it('sends tipo=ENTRADA from the Entradas filter, back on the first page', async () => {
    await mountView()
    await nextPageButton().trigger('click')
    await flushPromises()

    await filterButton('Entradas').trigger('click')
    await flushPromises()

    expect(api.findAllMovimentos).toHaveBeenCalledTimes(3)
    expect(api.findAllMovimentos).toHaveBeenLastCalledWith(undefined, 'ENTRADA', 0, 20, undefined)
  })

  it('loads the current product by id when editing and shows its description in the autocomplete', async () => {
    productsApi.findProdutoById.mockResolvedValue(okResponse(produto(7, 'Notebook Dell')))
    // A primeira página do autocomplete não traz o produto do movimento: ele entra mesmo assim
    productsApi.findAllProdutos.mockResolvedValue(pagedResponse([produto(1, 'Mouse'), produto(2, 'Teclado')]))
    await mountView()

    // Primeiro botão da primeira linha: editar (o segundo é excluir)
    await wrapper!.find('tbody tr button').trigger('click')
    await flushPromises()

    expect(productsApi.findProdutoById).toHaveBeenCalledExactlyOnceWith(7)
    expect(productsApi.findAllProdutos).toHaveBeenCalledWith(undefined, 0, 20)

    // O v-dialog vai por teleport para o body, fora do wrapper
    const input = document.body.querySelector<HTMLInputElement>('.v-autocomplete input')
    expect(input).not.toBeNull()
    expect(input!.value).toBe('Notebook Dell')
  })
})
