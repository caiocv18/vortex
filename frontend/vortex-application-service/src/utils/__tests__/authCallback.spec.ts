import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { defineComponent } from 'vue'
import { createRouter, createWebHistory, type Router } from 'vue-router'
import type { AuthCallbackData } from '@/utils/authCallback'
import type { LoginResponse } from '@/api/authHttp'
import { createMemoryStorage } from '@/__tests__/memoryStorage'
// Fonte de main.ts como texto (import ?raw do Vite), para conferir a ordem dos imports
import mainSource from '@/main.ts?raw'

const { requestSessionRefresh } = vi.hoisted(() => ({ requestSessionRefresh: vi.fn() }))
vi.mock('@/api/authHttp', () => ({ requestSessionRefresh }))

// O que o app de login põe na URL. O refresh token tem '%zz' e '%41': com decodificação dupla,
// '%zz' derrubava o callback (URIError) e '%41' virava 'A'.
const HAND_OFF: AuthCallbackData = {
  accessToken: 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl',
  refreshToken: 'refresh%zz-%41-1',
  user: {
    id: '1',
    email: 'ana@vortex.dev',
    username: 'ana',
    roles: ['USER'],
    isActive: true,
    isVerified: true,
  },
}

// O que o serviço devolve na troca: de propósito diferente do hand-off, para provar a origem do que é gravado
const SESSION: LoginResponse = {
  accessToken: 'eyJhbGciOiJIUzI1NiJ9.c2VydmVy.c2lnbmF0dXJlLXNlcnZlcg',
  refreshToken: 'refresh-from-server',
  tokenType: 'Bearer',
  expiresIn: 900,
  user: { ...HAND_OFF.user, id: '1', username: 'ana-do-servidor' },
}

// Como o app de login monta o hand-off: encodeURIComponent uma vez só
const encode = (value: unknown) => encodeURIComponent(JSON.stringify(value))

const setUrl = (pathAndMore: string) => window.history.replaceState(null, '', pathAndMore)

const Blank = defineComponent({ render: () => null })
const routers: Router[] = []
function createAppRouter(): Router {
  const router = createRouter({
    history: createWebHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: Blank }],
  })
  routers.push(router)
  return router
}

// Módulo novo a cada teste: a troca pendente é estado do módulo (uma por carga de página)
async function loadCallback() {
  vi.resetModules()
  return import('@/utils/authCallback')
}

describe('handleAuthCallback', () => {
  let storage: Storage
  let callback: Awaited<ReturnType<typeof loadCallback>>

  beforeEach(async () => {
    storage = createMemoryStorage()
    vi.stubGlobal('localStorage', storage)
    vi.spyOn(console, 'log').mockImplementation(() => undefined)
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    requestSessionRefresh.mockReset()
    requestSessionRefresh.mockResolvedValue(SESSION)
    callback = await loadCallback()
  })

  afterEach(() => {
    for (const router of routers.splice(0)) {
      router.options.history.destroy()
    }
    setUrl('/')
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  function storedSession() {
    const user = storage.getItem('vortex_user')
    return {
      accessToken: storage.getItem('accessToken'),
      refreshToken: storage.getItem('refreshToken'),
      user: user === null ? null : JSON.parse(user),
    }
  }

  const serverSession = {
    accessToken: SESSION.accessToken,
    refreshToken: SESSION.refreshToken,
    user: SESSION.user,
  }

  it('exchanges the refresh token of the fragment (decoded exactly once) and stores what the service returned', async () => {
    setUrl(`/movements#authData=${encode(HAND_OFF)}`)

    expect(callback.handleAuthCallback()).toBe(true)
    expect(await callback.awaitAuthHandOff()).toBe(true)

    expect(requestSessionRefresh).toHaveBeenCalledExactlyOnceWith('refresh%zz-%41-1')
    expect(storedSession()).toEqual(serverSession)
  })

  it('never stores the access token, user or roles that came in the URL', async () => {
    const forged = {
      ...HAND_OFF,
      accessToken: 'forged-access',
      user: { ...HAND_OFF.user, roles: ['ADMIN'] },
    }
    setUrl(`/movements#authData=${encode(forged)}`)

    callback.handleAuthCallback()
    await callback.awaitAuthHandOff()

    expect(storage.getItem('accessToken')).toBe(SESSION.accessToken)
    expect(storedSession().user.roles).toEqual(['USER'])
  })

  it('stores nothing when the service refuses the refresh token, and the URL is already clean', async () => {
    requestSessionRefresh.mockRejectedValue(new Error('Invalid or expired refresh token'))
    setUrl(`/movements#authData=${encode(HAND_OFF)}`)

    expect(callback.handleAuthCallback()).toBe(true)
    expect(window.location.href).toBe(`${window.location.origin}/movements`)
    expect(await callback.awaitAuthHandOff()).toBe(false)

    expect(storage).toHaveLength(0)
  })

  it('stores nothing when the service returns an incomplete session', async () => {
    requestSessionRefresh.mockResolvedValue({ ...SESSION, user: null })
    setUrl(`/movements#authData=${encode(HAND_OFF)}`)

    callback.handleAuthCallback()

    expect(await callback.awaitAuthHandOff()).toBe(false)
    expect(storage).toHaveLength(0)
  })

  it('still accepts the old query form, decoding it exactly once', async () => {
    setUrl(`/movements?authData=${encode(HAND_OFF)}`)

    expect(callback.handleAuthCallback()).toBe(true)
    await callback.awaitAuthHandOff()

    expect(requestSessionRefresh).toHaveBeenCalledExactlyOnceWith(HAND_OFF.refreshToken)
    expect(storedSession()).toEqual(serverSession)
  })

  it('removes the hand-off from the URL right away, keeping the page, the rest of the query and the history length', () => {
    setUrl(`/movements?tab=2#authData=${encode(HAND_OFF)}`)
    const historyLength = window.history.length

    callback.handleAuthCallback()

    expect(window.location.href).toBe(`${window.location.origin}/movements?tab=2`)
    expect(window.history).toHaveLength(historyLength)

    setUrl(`/products?tab=2&authData=${encode(HAND_OFF)}&q=x#secao`)
    callback.handleAuthCallback()
    expect(window.location.href).toBe(`${window.location.origin}/products?tab=2&q=x#secao`)
  })

  it('prefers the fragment when both forms are present and removes both', async () => {
    const fromQuery = { ...HAND_OFF, refreshToken: 'refresh-from-query' }
    setUrl(`/?authData=${encode(fromQuery)}#authData=${encode(HAND_OFF)}`)

    expect(callback.handleAuthCallback()).toBe(true)
    await callback.awaitAuthHandOff()

    expect(requestSessionRefresh).toHaveBeenCalledExactlyOnceWith(HAND_OFF.refreshToken)
    expect(window.location.href).toBe(`${window.location.origin}/`)
  })

  it('exchanges the refresh_token of the legacy fragment form', async () => {
    setUrl(
      `/reports#access_token=forged-access&refresh_token=refresh-legacy&user=${encode(HAND_OFF.user)}`,
    )

    expect(callback.handleAuthCallback()).toBe(true)
    await callback.awaitAuthHandOff()

    expect(requestSessionRefresh).toHaveBeenCalledExactlyOnceWith('refresh-legacy')
    expect(storedSession()).toEqual(serverSession)
    expect(window.location.href).toBe(`${window.location.origin}/reports`)
  })

  it('stores nothing and calls nothing for a broken hand-off, but still clears it from the URL', async () => {
    setUrl('/movements#authData=%7Bnot-json')
    expect(callback.handleAuthCallback()).toBe(false)
    expect(window.location.href).toBe(`${window.location.origin}/movements`)

    setUrl(`/movements#authData=${encode({ accessToken: 'only-access' })}`)
    expect(callback.handleAuthCallback()).toBe(false)
    expect(window.location.href).toBe(`${window.location.origin}/movements`)

    setUrl('/movements#access_token=only-access')
    expect(callback.handleAuthCallback()).toBe(false)
    expect(window.location.href).toBe(`${window.location.origin}/movements`)

    expect(await callback.awaitAuthHandOff()).toBe(false)
    expect(requestSessionRefresh).not.toHaveBeenCalled()
    expect(storage).toHaveLength(0)
  })

  it('leaves a URL without hand-off untouched and resolves the wait right away', async () => {
    setUrl('/products?tab=2#secao')

    expect(callback.handleAuthCallback()).toBe(false)

    expect(window.location.href).toBe(`${window.location.origin}/products?tab=2#secao`)
    expect(await callback.awaitAuthHandOff()).toBe(false)
    expect(requestSessionRefresh).not.toHaveBeenCalled()
    expect(storage).toHaveLength(0)
  })

  it('runs when authCallbackEntry is imported', async () => {
    setUrl(`/movements#authData=${encode(HAND_OFF)}`)
    vi.resetModules()

    await import('@/authCallbackEntry')
    const { awaitAuthHandOff } = await import('@/utils/authCallback')
    await awaitAuthHandOff()

    expect(storedSession()).toEqual(serverSession)
    expect(window.location.href).toBe(`${window.location.origin}/movements`)
  })
})

// O createWebHistory guarda a URL corrente quando o router é criado e a regrava (replaceState)
// na primeira navegação. Por isso o callback roda antes de ./router ser avaliado.
describe('auth hand-off and the router', () => {
  let callback: Awaited<ReturnType<typeof loadCallback>>

  beforeEach(async () => {
    vi.stubGlobal('localStorage', createMemoryStorage())
    vi.spyOn(console, 'log').mockImplementation(() => undefined)
    requestSessionRefresh.mockReset()
    requestSessionRefresh.mockResolvedValue(SESSION)
    callback = await loadCallback()
  })

  afterEach(() => {
    for (const router of routers.splice(0)) {
      router.options.history.destroy()
    }
    setUrl('/')
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('main.ts imports authCallbackEntry before any other module', () => {
    const imports = mainSource.match(/^import\s.*$/gm) ?? []

    expect(imports[0]).toBe("import './authCallbackEntry'")
  })

  it('a router created after the callback never writes the hand-off back', async () => {
    setUrl(`/movements?tab=2#authData=${encode(HAND_OFF)}`)
    callback.handleAuthCallback()

    const router = createAppRouter()
    await router.push(router.options.history.location)

    expect(router.currentRoute.value.fullPath).toBe('/movements?tab=2')
    expect(window.location.href).toBe(`${window.location.origin}/movements?tab=2`)
  })

  it('a router created before the callback writes the hand-off back (the trap the import order avoids)', async () => {
    setUrl(`/movements#authData=${encode(HAND_OFF)}`)
    const router = createAppRouter()
    callback.handleAuthCallback()

    await router.push(router.options.history.location)

    expect(window.location.hash).toContain('authData=')
  })
})
