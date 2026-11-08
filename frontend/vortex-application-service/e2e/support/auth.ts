import process from 'node:process'
import { fileURLToPath } from 'node:url'

// Constantes de autenticação compartilhadas pelo setup (auth.setup.ts) e pelos specs.

const withoutTrailingSlash = (url: string) => url.replace(/\/+$/, '')

/** Backend do serviço de autorização (Quarkus): register, login e refresh. */
export const AUTH_API_URL = withoutTrailingSlash(
  process.env.PLAYWRIGHT_AUTH_API_URL || 'http://localhost:8081',
)

/** Frontend de login (React), para onde o guard do router manda quem não tem sessão. */
export const AUTH_APP_URL = withoutTrailingSlash(
  process.env.PLAYWRIGHT_AUTH_APP_URL || 'http://localhost:3001',
)

/**
 * storageState gerado pelo projeto "setup". É o mesmo arquivo que playwright.config.ts
 * entrega aos projetos de browser (lá resolvido a partir do próprio config). Fica fora do git.
 */
export const AUTH_STATE_FILE = fileURLToPath(
  new URL('../../playwright/.auth/user.json', import.meta.url),
)

/** Chaves de localStorage que o app lê para considerar a sessão aberta. */
export const AUTH_STORAGE_KEYS = {
  accessToken: 'accessToken',
  refreshToken: 'refreshToken',
  user: 'vortex_user',
} as const
