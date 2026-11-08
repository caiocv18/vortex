import type { AxiosRequestConfig } from 'axios'

// Resposta do POST /api/auth/refresh no formato do serviço de autorização
export function refreshResponse(accessToken: string, refreshToken: string) {
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

// Serviço de autorização que aceita a conexão e nunca responde (container pausado, JVM travada).
// Implementação para axios.post: a promise nunca se resolve; `signals` guarda o AbortSignal que
// cada chamada recebeu, para o teste conferir que o app abortou a requisição.
export function hangingAuthService() {
  const signals: AbortSignal[] = []
  const post = (_url: string, _body?: unknown, config?: AxiosRequestConfig) => {
    if (config?.signal) {
      signals.push(config.signal as AbortSignal)
    }
    return new Promise<never>(() => undefined)
  }
  return { post, signals }
}
