import { describe, it, expect, afterEach } from 'vitest'
import { AUTH_FRONTEND_URL, buildAuthPageUrl, currentPageUrl } from '@/api/authService'

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
