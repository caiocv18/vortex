// Paginação no servidor compartilhada pelas stores de produtos e movimentos.
// A tabela (v-data-table-server) conta páginas a partir de 1; a API (Spring Data) a partir de 0.

// Mesmo formato do sortBy do Vuetify: order ausente ou true conta como crescente
export interface SortItem {
  key: string
  order?: boolean | 'asc' | 'desc'
}

// O que o @update:options do v-data-table-server entrega (só os campos usados aqui)
export interface TableOptions {
  page: number
  itemsPerPage: number
  sortBy: SortItem[]
  search?: string
}

export const ITEMS_PER_PAGE_OPTIONS = [10, 20, 50, 100]
export const DEFAULT_ITEMS_PER_PAGE = 20
// Atraso da busca de texto: uma requisição quando o usuário para de digitar, não uma por tecla
export const SEARCH_DEBOUNCE_MS = 300
// Tamanho da lista do autocomplete de produtos
export const AUTOCOMPLETE_SIZE = 20

// sortBy da tabela → parâmetro sort repetível da API ("prop,asc" | "prop,desc"); vazio usa o padrão do servidor
export function toSortParam(sortBy: readonly SortItem[]): string[] | undefined {
  if (sortBy.length === 0) return undefined
  return sortBy.map(({ key, order }) => `${key},${order === 'desc' ? 'desc' : 'asc'}`)
}

// Página 1 da tabela é a página 0 da API
export function toApiPage(page: number): number {
  return Math.max(page, 1) - 1
}

// Texto vazio ou só espaços não filtra: não vai na URL
export function toQueryText(q: string): string | undefined {
  const text = q.trim()
  return text === '' ? undefined : text
}

function sameSort(a: readonly SortItem[], b: readonly SortItem[]): boolean {
  return (
    a.length === b.length &&
    a.every((item, i) => item.key === b[i]!.key && (item.order === 'desc') === (b[i]!.order === 'desc'))
  )
}

// A tabela reemite as opções quando a própria store muda a página (filtro, exclusão que esvaziou a
// última página). Se as opções já são as da página carregada, não há o que buscar de novo.
export function isSameTableState(
  options: TableOptions,
  state: { page: number; itemsPerPage: number; sortBy: readonly SortItem[]; q: string },
): boolean {
  return (
    options.page === state.page &&
    options.itemsPerPage === state.itemsPerPage &&
    (options.search ?? '') === state.q &&
    sameSort(options.sortBy, state.sortBy)
  )
}
