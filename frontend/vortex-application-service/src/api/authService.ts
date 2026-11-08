// Fonte única das URLs do serviço de autorização.
// Lê as variáveis já declaradas em .env; os valores padrão são os mesmos que antes
// estavam fixos no código (api/config.ts e stores/auth.ts).
// Este módulo não importa store nem cliente HTTP, para não criar import circular.

const withoutTrailingSlash = (url: string) => url.replace(/\/+$/, '')

/** Backend do serviço de autorização (login, refresh e logout de tokens). */
export const AUTH_BACKEND_URL = withoutTrailingSlash(
  import.meta.env.VITE_AUTH_BACKEND_URL || 'http://localhost:8081',
)

/** Frontend do serviço de autorização (telas de login e cadastro). */
export const AUTH_FRONTEND_URL = withoutTrailingSlash(
  import.meta.env.VITE_AUTH_SERVICE_URL || 'http://localhost:3001',
)

export const AUTH_REFRESH_URL = `${AUTH_BACKEND_URL}/api/auth/refresh`
export const AUTH_LOGOUT_URL = `${AUTH_BACKEND_URL}/api/auth/logout`

/**
 * Tempo máximo de uma chamada ao backend do serviço de autorização (refresh e logout).
 * Um serviço que aceita a conexão e não responde (container pausado, JVM travada) não pode
 * segurar a sessão: passado esse tempo a chamada é abortada e o app segue o caminho de falha.
 */
export const AUTH_REQUEST_TIMEOUT_MS = 10_000

/** Telas do app de login. */
export type AuthPage = 'login' | 'register'

/**
 * URL de uma tela do app de login levando `returnUrl` na query (`?returnUrl=`, codificado uma vez).
 * O app de login volta para `returnUrl` quando ela é da origem desta aplicação; sem o parâmetro,
 * ou com URL de outra origem, volta para a URL padrão dele (a raiz desta aplicação).
 * A volta traz a sessão no fragmento (`#authData=...`), que handleAuthCallback
 * (utils/authCallback.ts) consome antes de o router ler a URL.
 */
export function buildAuthPageUrl(page: AuthPage, returnUrl: string): string {
  return `${AUTH_FRONTEND_URL}/${page}?returnUrl=${encodeURIComponent(returnUrl)}`
}

/** Página atual sem o fragmento: é no fragmento que o app de login devolve a sessão. */
export function currentPageUrl(): string {
  const { origin, pathname, search } = window.location
  return `${origin}${pathname}${search}`
}

/** Leva o usuário para uma tela do app de login; depois de entrar ele volta para a página atual. */
export function redirectToAuthPage(page: AuthPage): void {
  window.location.href = buildAuthPageUrl(page, currentPageUrl())
}

/** Atalho de redirectToAuthPage('login'), usado pelo interceptor 401 de api/config.ts. */
export function redirectToLoginPage(): void {
  redirectToAuthPage('login')
}
