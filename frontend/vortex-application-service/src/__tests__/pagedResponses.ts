import { AxiosHeaders, type AxiosResponse } from 'axios'
import type { PageMetadata } from '@/api'

function response<T>(data: T, status: number): AxiosResponse<T> {
  return { data, status, statusText: 'OK', headers: {}, config: { headers: new AxiosHeaders() } }
}

// Resposta paginada da API ({content, page}), como o cliente gerado a recebe do axios
export function pagedResponse<T>(
  content: T[],
  { totalElements = content.length, number = 0, size = 20 } = {},
): AxiosResponse<{ content: T[]; page: PageMetadata }> {
  return response(
    {
      content,
      page: { size, number, totalElements, totalPages: Math.ceil(totalElements / size) },
    },
    200,
  )
}

// Resposta de uma operação que devolve um objeto só (create, update, findById) ou nada (delete)
export function okResponse<T>(data: T, status = 200): AxiosResponse<T> {
  return response(data, status)
}
