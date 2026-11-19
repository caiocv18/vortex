import { describe, it, expect, afterEach, vi } from 'vitest'
import { AUTH_FRONTEND_URL, buildAuthPageUrl, currentPageUrl } from '@/api/authService'

describe('auth service URLs from .env', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  it.each([
    ['http://auth.example:8081', 'http://login.example', 'http://auth.example:8081', 'http://login.example'],
    ['http://auth.example:8081/', 'http://login.example/', 'http://auth.example:8081', 'http://login.example'],
    ['http://auth.example:8081///', 'http://login.example/app//', 'http://auth.example:8081', 'http://login.example/app'],
  ])('drops the trailing slashes of %j and %j', async (backend, frontend, expectedBackend, expectedFrontend) => {
    vi.stubEnv('VITE_AUTH_BACKEND_URL', backend)
    vi.stubEnv('VITE_AUTH_SERVICE_URL', frontend)
    vi.resetModules()

    const urls = await import('@/api/authService')

    expect(urls.AUTH_BACKEND_URL).toBe(expectedBackend)
    expect(urls.AUTH_FRONTEND_URL).toBe(expectedFrontend)
    expect(urls.AUTH_REFRESH_URL).toBe(`${expectedBackend}/api/auth/refresh`)
    expect(urls.AUTH_LOGOUT_URL).toBe(`${expectedBackend}/api/auth/logout`)
  })

  it('falls back to the local defaults when the variables are empty', async () => {
    vi.stubEnv('VITE_AUTH_BACKEND_URL', '')
    vi.stubEnv('VITE_AUTH_SERVICE_URL', '')
    vi.resetModules()

    const urls = await import('@/api/authService')

    expect(urls.AUTH_BACKEND_URL).toBe('http://localhost:8081')
    expect(urls.AUTH_FRONTEND_URL).toBe('http://localhost:3001')
  })
})

describe('auth app URLs', () => {
  afterEach(() => {
    window.history.replaceState(null, '', '/')
  })

  it('sends the page to return to in ?returnUrl=, encoded once', () => {
    const returnUrl = 'http://localhost:5173/movements?tab=2&q=50%25 off'

    const url = new URL(buildAuthPageUrl('login', returnUrl))

    expect(`${url.origin}${url.pathname}`).toBe(`${AUTH_FRONTEND_URL}/login`)
    expect([...url.searchParams.keys()]).toEqual(['returnUrl'])
    // É assim que o app de login lê o parâmetro: URLSearchParams.get, uma decodificação só
    expect(url.searchParams.get('returnUrl')).toBe(returnUrl)
  })

  it('builds the register page the same way', () => {
    expect(buildAuthPageUrl('register', 'http://localhost:5173/')).toBe(
      `${AUTH_FRONTEND_URL}/register?returnUrl=${encodeURIComponent('http://localhost:5173/')}`,
    )
  })

  it('uses the current page without its fragment as the return page', () => {
    window.history.replaceState(null, '', '/movements?tab=2#authData=old')

    expect(currentPageUrl()).toBe(`${window.location.origin}/movements?tab=2`)
  })
})
