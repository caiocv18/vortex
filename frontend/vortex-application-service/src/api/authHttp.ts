// Chamadas HTTP ao backend do serviço de autorização, compartilhadas pela store de auth e pelo
// hand-off do login (utils/authCallback.ts). Não importa store nem router: o hand-off roda antes
// de o router existir (ver authCallbackEntry.ts).
import axios, { type AxiosResponse } from 'axios'
import { AUTH_REFRESH_URL, AUTH_REQUEST_TIMEOUT_MS } from '@/api/authService'

export interface User {
  id: string
  email: string
  username: string
  roles: string[]
  lastLogin?: string
  isActive: boolean
  isVerified: boolean
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: User
}

// Envelope das respostas do serviço de autorização: { success, message, data }
export interface AuthServiceResponse<T> {
  success: boolean
  message?: string
  data?: T
}

// POST ao backend do serviço de autorização que sempre termina em até AUTH_REQUEST_TIMEOUT_MS.
// O axios global não tem timeout (o `timeout` de api/config.ts vale só para o apiClient), e o
// refresh é single-flight: uma chamada pendurada deixava todo 401 esperando a mesma promise,
// sem erro e sem redirecionamento. O timer rejeita a chamada mesmo que o transporte ignore o
// abort; o abort cancela a requisição de verdade no browser.
export async function postToAuthService<T>(url: string, body: unknown): Promise<AxiosResponse<T>> {
  const controller = new AbortController()
  let timer: ReturnType<typeof setTimeout> | undefined
  const timedOut = new Promise<never>((_, reject) => {
    timer = setTimeout(() => {
      reject(new Error(`Auth service did not answer within ${AUTH_REQUEST_TIMEOUT_MS} ms: ${url}`))
      controller.abort()
    }, AUTH_REQUEST_TIMEOUT_MS)
  })
  try {
    return await Promise.race([axios.post<T>(url, body, { signal: controller.signal }), timedOut])
  } finally {
    clearTimeout(timer)
  }
}

/**
 * Troca um refresh token por uma sessão emitida pelo serviço (POST /api/auth/refresh).
 * Rejeita quando o serviço recusa ou devolve uma sessão incompleta.
 */
export async function requestSessionRefresh(refreshToken: string): Promise<LoginResponse> {
  const response = await postToAuthService<AuthServiceResponse<LoginResponse>>(AUTH_REFRESH_URL, {
    refreshToken,
  })
  const payload = response.data?.data
  if (response.data?.success === false || !payload?.accessToken) {
    throw new Error(response.data?.message || 'Invalid refresh response')
  }
  return payload
}
