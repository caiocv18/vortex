import { describe, it, expect, beforeEach, afterEach, vi, type MockInstance } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import axios, {
  AxiosError,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { apiClient } from '@/api/config'
import { AUTH_REFRESH_URL, redirectToLoginPage } from '@/api/authService'

// O redirecionamento real troca window.location (o jsdom não navega): substitui só essa função
vi.mock('@/api/authService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/authService')>()
  return { ...actual, redirectToLoginPage: vi.fn() }
})

// localStorage em memória (o interceptor e a store de auth leem os tokens de lá)
function createMemoryStorage(): Storage {
  const data = new Map<string, string>()
  return {
    get length() {
      return data.size
    },
    clear: () => data.clear(),
    getItem: (key: string) => data.get(key) ?? null,
    key: (index: number) => [...data.keys()][index] ?? null,
    removeItem: (key: string) => {
      data.delete(key)
    },
    setItem: (key: string, value: string) => {
      data.set(key, String(value))
    },
  }
}

// Backend da aplicação simulado: responde conforme o Authorization recebido e registra cada chamada
function createBackend(statusFor: (authorization: string) => number) {
  const seen: string[] = []
  const adapter = vi.fn<AxiosAdapter>(async (config: InternalAxiosRequestConfig) => {
    const authorization = String(config.headers?.Authorization ?? '')
    seen.push(authorization)
    const status = statusFor(authorization)
    const response: AxiosResponse = {
      data: status === 200 ? { ok: true } : { message: 'Unauthorized' },
      status,
      statusText: String(status),
      headers: {},
      config,
      request: {},
    }
    if (status >= 200 && status < 300) {
      return response
    }
    throw new AxiosError(
      `Request failed with status code ${status}`,
      AxiosError.ERR_BAD_REQUEST,
      config,
      {},
      response,
    )
  })
  return { adapter, seen }
}

// Resposta do POST /api/auth/refresh no formato do serviço de autorização
function refreshResponse(accessToken: string, refreshToken: string) {
  return {
    data: {
      success: true,
      message: 'Token refreshed successfully',
      data: {
        accessToken,
        refreshToken,
        tokenType: 'Bearer',
        expiresIn: 900,
        user: {
          id: '1',
          email: 'ana@vortex.dev',
          username: 'ana',
          roles: ['USER'],
          isActive: true,
          isVerified: true,
        },
      },
    },
    status: 200,
    statusText: 'OK',
    headers: {},
    config: {},
  }
}

const NEW_TOKEN_OK = (authorization: string) => (authorization === 'Bearer access-new' ? 200 : 401)

describe('apiClient session refresh on 401', () => {
  const originalAdapter = apiClient.defaults.adapter
  let storage: Storage
  let postSpy: MockInstance<typeof axios.post>

  beforeEach(() => {
    setActivePinia(createPinia())
    storage = createMemoryStorage()
    vi.stubGlobal('localStorage', storage)
    storage.setItem('accessToken', 'access-old')
    storage.setItem('refreshToken', 'refresh-1')

    postSpy = vi.spyOn(axios, 'post')
    vi.mocked(redirectToLoginPage).mockClear()

    // Os interceptors e o logger escrevem bastante no console
    for (const method of ['log', 'debug', 'info', 'warn', 'error'] as const) {
      vi.spyOn(console, method).mockImplementation(() => undefined)
    }
  })

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('refreshes the token and retries the original request once', async () => {
    const backend = createBackend(NEW_TOKEN_OK)
    apiClient.defaults.adapter = backend.adapter
    postSpy.mockResolvedValue(refreshResponse('access-new', 'refresh-2'))

    const response = await apiClient.get('/tipos-produto')

    expect(response.status).toBe(200)
    expect(response.data).toEqual({ ok: true })
    expect(postSpy).toHaveBeenCalledTimes(1)
    expect(postSpy).toHaveBeenCalledWith(AUTH_REFRESH_URL, { refreshToken: 'refresh-1' })
    expect(backend.seen).toEqual(['Bearer access-old', 'Bearer access-new'])
    expect(storage.getItem('accessToken')).toBe('access-new')
    expect(storage.getItem('refreshToken')).toBe('refresh-2')
    expect(redirectToLoginPage).not.toHaveBeenCalled()
  })

  it('shares a single refresh between two concurrent 401s', async () => {
    const backend = createBackend(NEW_TOKEN_OK)
    apiClient.defaults.adapter = backend.adapter
    let resolveRefresh: (value: unknown) => void = () => undefined
    postSpy.mockReturnValue(
      new Promise((resolve) => {
        resolveRefresh = resolve
      }),
    )

    const requests = Promise.all([apiClient.get('/produtos'), apiClient.get('/movimentos')])

    // Os dois 401 chegam ao interceptor antes de o refresh responder
    await vi.waitFor(() => expect(backend.seen).toHaveLength(2))
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(postSpy).toHaveBeenCalledTimes(1)

    resolveRefresh(refreshResponse('access-new', 'refresh-2'))
    const responses = await requests

    expect(responses.map((r) => r.status)).toEqual([200, 200])
    expect(postSpy).toHaveBeenCalledTimes(1)
    expect(backend.seen).toEqual([
      'Bearer access-old',
      'Bearer access-old',
      'Bearer access-new',
      'Bearer access-new',
    ])
    expect(redirectToLoginPage).not.toHaveBeenCalled()
  })

  it('clears the tokens and redirects to login when the refresh fails', async () => {
    const backend = createBackend(() => 401)
    apiClient.defaults.adapter = backend.adapter
    postSpy.mockRejectedValue(
      new AxiosError('Request failed with status code 401', AxiosError.ERR_BAD_REQUEST),
    )

    await expect(apiClient.get('/produtos')).rejects.toMatchObject({ response: { status: 401 } })

    expect(postSpy).toHaveBeenCalledTimes(1)
    expect(backend.seen).toEqual(['Bearer access-old'])
    expect(storage.getItem('accessToken')).toBeNull()
    expect(storage.getItem('refreshToken')).toBeNull()
    expect(redirectToLoginPage).toHaveBeenCalledTimes(1)
  })

  it('does not loop when the retried request gets a second 401', async () => {
    const backend = createBackend(() => 401)
    apiClient.defaults.adapter = backend.adapter
    postSpy.mockResolvedValue(refreshResponse('access-new', 'refresh-2'))

    await expect(apiClient.get('/produtos')).rejects.toMatchObject({ response: { status: 401 } })

    expect(postSpy).toHaveBeenCalledTimes(1)
    expect(backend.seen).toEqual(['Bearer access-old', 'Bearer access-new'])
    expect(storage.getItem('accessToken')).toBeNull()
    expect(storage.getItem('refreshToken')).toBeNull()
    expect(redirectToLoginPage).toHaveBeenCalledTimes(1)
  })

  it('redirects without calling the auth service when there is no refresh token', async () => {
    storage.removeItem('refreshToken')
    const backend = createBackend(() => 401)
    apiClient.defaults.adapter = backend.adapter

    await expect(apiClient.get('/produtos')).rejects.toMatchObject({ response: { status: 401 } })

    expect(postSpy).not.toHaveBeenCalled()
    expect(backend.seen).toEqual(['Bearer access-old'])
    expect(storage.getItem('accessToken')).toBeNull()
    expect(redirectToLoginPage).toHaveBeenCalledTimes(1)
  })
})
