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

/** Leva o usuário para a tela de login do serviço de autorização, com retorno para a página atual. */
export function redirectToLoginPage(): void {
  const returnUrl = encodeURIComponent(window.location.href)
  window.location.href = `${AUTH_FRONTEND_URL}/login?returnUrl=${returnUrl}`
}
