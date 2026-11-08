import { describe, it, expect, beforeEach, afterEach, vi, type MockInstance } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import {
  AUTH_LOGOUT_URL,
  AUTH_REFRESH_URL,
  AUTH_REQUEST_TIMEOUT_MS,
  redirectToAuthPage,
} from '@/api/authService'
import { createMemoryStorage } from '@/__tests__/memoryStorage'
import { hangingAuthService, refreshResponse } from '@/__tests__/authServiceFakes'

// O redirecionamento real troca window.location (o jsdom não navega): substitui só essa função
vi.mock('@/api/authService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/authService')>()
  return { ...actual, redirectToAuthPage: vi.fn() }
})

describe('auth store', () => {
  let storage: Storage
  let session: Storage
  let postSpy: MockInstance<typeof axios.post>

  beforeEach(() => {
    // Só os timers: promises continuam reais
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    setActivePinia(createPinia())
    storage = createMemoryStorage()
    session = createMemoryStorage()
    vi.stubGlobal('localStorage', storage)
    vi.stubGlobal('sessionStorage', session)
    storage.setItem('accessToken', 'access-old')
    storage.setItem('refreshToken', 'refresh-1')
    storage.setItem('vortex_user', JSON.stringify({ id: '1', email: 'ana@vortex.dev' }))

    postSpy = vi.spyOn(axios, 'post')
    vi.mocked(redirectToAuthPage).mockClear()

    for (const method of ['log', 'debug', 'info', 'warn', 'error'] as const) {
      vi.spyOn(console, method).mockImplementation(() => undefined)
    }
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('sends the user to the auth app page, which carries the current page as returnUrl', () => {
    const store = useAuthStore()

    store.redirectToAuth('register')
    store.redirectToAuth()

    expect(redirectToAuthPage).toHaveBeenNthCalledWith(1, 'register')
    expect(redirectToAuthPage).toHaveBeenNthCalledWith(2, 'login')
    // O sessionStorage é por origem: o app de login nunca o lia, então não se grava mais nada nele
    expect(session.length).toBe(0)
  })

  it('gives up on a refresh the auth service never answers and lets the next refresh start over', async () => {
    const authService = hangingAuthService()
    postSpy.mockImplementation(authService.post)
    const store = useAuthStore()

    // Chamadas concorrentes dividem a mesma renovação (single-flight)
    const first = store.attemptTokenRefresh()
    const second = store.attemptTokenRefresh()
    await vi.advanceTimersByTimeAsync(AUTH_REQUEST_TIMEOUT_MS - 1)
    expect(postSpy).toHaveBeenCalledTimes(1)
    expect(authService.signals[0]!.aborted).toBe(false)

    await vi.advanceTimersByTimeAsync(1)
    await expect(first).resolves.toBe(false)
    await expect(second).resolves.toBe(false)
    expect(authService.signals[0]!.aborted).toBe(true)
    expect(storage.getItem('accessToken')).toBeNull()
    expect(storage.getItem('refreshToken')).toBeNull()
    expect(storage.getItem('vortex_user')).toBeNull()

    // A renovação pendurada não fica presa no single-flight: a próxima é uma chamada nova
    storage.setItem('refreshToken', 'refresh-2')
    postSpy.mockResolvedValue(refreshResponse('access-new', 'refresh-3'))
    await expect(store.attemptTokenRefresh()).resolves.toBe(true)
    expect(postSpy).toHaveBeenCalledTimes(2)
    expect(postSpy).toHaveBeenLastCalledWith(
      AUTH_REFRESH_URL,
      { refreshToken: 'refresh-2' },
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    expect(storage.getItem('accessToken')).toBe('access-new')
    expect(storage.getItem('refreshToken')).toBe('refresh-3')
  })

  it('logs out locally when the auth service never answers the logout', async () => {
    const authService = hangingAuthService()
    postSpy.mockImplementation(authService.post)
    const store = useAuthStore()

    const done = store.logout()
    await vi.advanceTimersByTimeAsync(AUTH_REQUEST_TIMEOUT_MS - 1)
    expect(redirectToAuthPage).not.toHaveBeenCalled()
    expect(storage.getItem('accessToken')).toBe('access-old')

    await vi.advanceTimersByTimeAsync(1)
    await done
    expect(postSpy).toHaveBeenCalledWith(
      AUTH_LOGOUT_URL,
      { refreshToken: 'refresh-1' },
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    expect(authService.signals[0]!.aborted).toBe(true)
    expect(storage.getItem('accessToken')).toBeNull()
    expect(storage.getItem('refreshToken')).toBeNull()
    expect(storage.getItem('vortex_user')).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(redirectToAuthPage).toHaveBeenCalledExactlyOnceWith('login')
  })
})
