import { describe, it, expect } from 'vitest'
import { isSameTableState, toApiPage, toQueryText, toSortParam } from '@/stores/pagination'

describe('pagination helpers', () => {
  it('turns the table sortBy into the repeatable sort parameter of the API', () => {
    expect(toSortParam([])).toBeUndefined()
    expect(toSortParam([{ key: 'descricao' }, { key: 'id', order: 'desc' }, { key: 'valorFornecedor', order: 'asc' }]))
      .toEqual(['descricao,asc', 'id,desc', 'valorFornecedor,asc'])
  })

  it('maps the first table page to page 0 of the API', () => {
    expect(toApiPage(1)).toBe(0)
    expect(toApiPage(4)).toBe(3)
    expect(toApiPage(0)).toBe(0)
  })

  it('leaves blank search text out of the request', () => {
    expect(toQueryText('')).toBeUndefined()
    expect(toQueryText('   ')).toBeUndefined()
    expect(toQueryText(' note ')).toBe('note')
  })

  it('tells when the table options are the page the store already has', () => {
    const state = { page: 2, itemsPerPage: 20, sortBy: [{ key: 'id', order: 'desc' as const }], q: 'note' }

    expect(isSameTableState({ page: 2, itemsPerPage: 20, sortBy: [{ key: 'id', order: 'desc' }], search: 'note' }, state)).toBe(true)
    expect(isSameTableState({ page: 1, itemsPerPage: 20, sortBy: [{ key: 'id', order: 'desc' }], search: 'note' }, state)).toBe(false)
    expect(isSameTableState({ page: 2, itemsPerPage: 50, sortBy: [{ key: 'id', order: 'desc' }], search: 'note' }, state)).toBe(false)
    expect(isSameTableState({ page: 2, itemsPerPage: 20, sortBy: [{ key: 'id', order: 'asc' }], search: 'note' }, state)).toBe(false)
    expect(isSameTableState({ page: 2, itemsPerPage: 20, sortBy: [], search: 'note' }, state)).toBe(false)
    expect(isSameTableState({ page: 2, itemsPerPage: 20, sortBy: [{ key: 'id', order: 'desc' }] }, state)).toBe(false)
  })
})
