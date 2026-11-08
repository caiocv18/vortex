import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { defineComponent } from 'vue'
import { createRouter, createWebHistory, type Router } from 'vue-router'
import { handleAuthCallback, type AuthCallbackData } from '@/utils/authCallback'
import { createMemoryStorage } from '@/__tests__/memoryStorage'
// Fonte de main.ts como texto (import ?raw do Vite), para conferir a ordem dos imports
import mainSource from '@/main.ts?raw'

// '%' é válido na parte local de um e-mail (yup, type="email" e @Email do Hibernate aceitam).
// Com decodificação dupla, '%zz' derrubava o callback (URIError) e '%41' virava 'A'.
const DATA: AuthCallbackData = {
  accessToken: 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl',
  refreshToken: 'refresh-token-1',
  user: {
    id: '1',
    email: 'ana%zz@vortex.dev',
    username: 'ana%41+teste',
    roles: ['USER'],
    isActive: true,
    isVerified: true,
  },
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

describe('handleAuthCallback', () => {
  let storage: Storage

  beforeEach(() => {
    storage = createMemoryStorage()
    vi.stubGlobal('localStorage', storage)
    vi.spyOn(console, 'log').mockImplementation(() => undefined)
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
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

  it('reads the hand-off from the fragment, decoding it exactly once', () => {
    setUrl(`/movements#authData=${encode(DATA)}`)

    expect(handleAuthCallback()).toBe(true)

    expect(storedSession()).toEqual(DATA)
    expect(storedSession().user.email).toBe('ana%zz@vortex.dev')
    expect(storedSession().user.username).toBe('ana%41+teste')
  })

  it('still accepts the old query form, decoding it exactly once', () => {
    setUrl(`/movements?authData=${encode(DATA)}`)

    expect(handleAuthCallback()).toBe(true)

    expect(storedSession()).toEqual(DATA)
  })

  it('removes the hand-off from the URL right away, keeping the page, the rest of the query and the history length', () => {
    setUrl(`/movements?tab=2#authData=${encode(DATA)}`)
    const historyLength = window.history.length

    handleAuthCallback()

    expect(window.location.href).toBe(`${window.location.origin}/movements?tab=2`)
    expect(window.history.length).toBe(historyLength)

    setUrl(`/products?tab=2&authData=${encode(DATA)}&q=x#secao`)
    handleAuthCallback()
    expect(window.location.href).toBe(`${window.location.origin}/products?tab=2&q=x#secao`)
  })

  it('prefers the fragment when both forms are present and removes both', () => {
    const fromQuery = { ...DATA, accessToken: 'access-from-query' }
    setUrl(`/?authData=${encode(fromQuery)}#authData=${encode(DATA)}`)

    expect(handleAuthCallback()).toBe(true)

    expect(storage.getItem('accessToken')).toBe(DATA.accessToken)
    expect(window.location.href).toBe(`${window.location.origin}/`)
  })

  it('reads the legacy fragment form (access_token, refresh_token, user) decoding once', () => {
    setUrl(
      `/reports#access_token=${DATA.accessToken}&refresh_token=${DATA.refreshToken}&user=${encode(DATA.user)}`,
    )

    expect(handleAuthCallback()).toBe(true)

    expect(storedSession()).toEqual(DATA)
    expect(window.location.href).toBe(`${window.location.origin}/reports`)
  })

  it('stores nothing for a broken or incomplete hand-off, but still clears it from the URL', () => {
    setUrl('/movements#authData=%7Bnot-json')
    expect(handleAuthCallback()).toBe(false)
    expect(window.location.href).toBe(`${window.location.origin}/movements`)

    setUrl(`/movements#authData=${encode({ accessToken: 'only-access' })}`)
    expect(handleAuthCallback()).toBe(false)
    expect(window.location.href).toBe(`${window.location.origin}/movements`)

    expect(storage.length).toBe(0)
  })

  it('leaves a URL without hand-off untouched', () => {
    setUrl('/products?tab=2#secao')

    expect(handleAuthCallback()).toBe(false)

    expect(window.location.href).toBe(`${window.location.origin}/products?tab=2#secao`)
    expect(storage.length).toBe(0)
  })

  it('runs when authCallbackEntry is imported', async () => {
    setUrl(`/movements#authData=${encode(DATA)}`)
    vi.resetModules()

    await import('@/authCallbackEntry')

    expect(storedSession()).toEqual(DATA)
    expect(window.location.href).toBe(`${window.location.origin}/movements`)
  })
})

// O createWebHistory guarda a URL corrente quando o router é criado e a regrava (replaceState)
// na primeira navegação. Por isso o callback roda antes de ./router ser avaliado.
describe('auth hand-off and the router', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createMemoryStorage())
    vi.spyOn(console, 'log').mockImplementation(() => undefined)
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
    setUrl(`/movements?tab=2#authData=${encode(DATA)}`)
    handleAuthCallback()

    const router = createAppRouter()
    await router.push(router.options.history.location)

    expect(router.currentRoute.value.fullPath).toBe('/movements?tab=2')
    expect(window.location.href).toBe(`${window.location.origin}/movements?tab=2`)
  })

  it('a router created before the callback writes the hand-off back (the trap the import order avoids)', async () => {
    setUrl(`/movements#authData=${encode(DATA)}`)
    const router = createAppRouter()
    handleAuthCallback()

    await router.push(router.options.history.location)

    expect(window.location.hash).toContain('authData=')
  })
})
