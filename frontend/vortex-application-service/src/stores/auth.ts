import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { AUTH_LOGOUT_URL, redirectToAuthPage, type AuthPage } from '@/api/authService'
import { postToAuthService, requestSessionRefresh, type User } from '@/api/authHttp'
import { createLogger } from '@/utils/logger'

export type { User, LoginResponse } from '@/api/authHttp'

// Não depende do estado da store: fica fora do setup e a store só o reexpõe
function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    const currentTime = Date.now() / 1000
    return payload.exp < currentTime
  } catch {
    return true
  }
}

export const useAuthStore = defineStore('auth', () => {
  const logger = createLogger('AuthStore')
  
  const token = ref<string | null>(localStorage.getItem('accessToken'))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const user = ref<User | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const isAuthenticated = computed(() => !!token.value && !!user.value)

  // Renovação em andamento (single-flight): chamadas concorrentes recebem a mesma promise
  let refreshInFlight: Promise<boolean> | null = null

  // Initialize user from localStorage
  const storedUser = localStorage.getItem('vortex_user')
  if (storedUser) {
    try {
      user.value = JSON.parse(storedUser)
    } catch (e) {
      logger.error('Error parsing stored user data', e)
    }
  }

  // Note: apiClient interceptors are already set up in api/config.ts
  // No need to duplicate them here

  function redirectToAuth(action: AuthPage = 'login'): void {
    logger.userAction(`Redirecting to auth service: ${action}`)
    // A página atual vai em ?returnUrl= (ver buildAuthPageUrl). O sessionStorage não serve para
    // isso: é por origem, e o app de login (outra origem) nunca enxergava o que era gravado aqui.
    redirectToAuthPage(action)
  }


  async function logout(): Promise<void> {
    logger.userAction('User logout initiated')
    
    try {
      // Call logout endpoint if refresh token exists
      if (refreshToken.value) {
        logger.apiCall('POST', '/api/auth/logout')
        // Com prazo: um serviço de autorização que não responde não pode prender o usuário logado
        await postToAuthService(AUTH_LOGOUT_URL, {
          refreshToken: refreshToken.value
        })
        logger.info('Server logout successful')
      }
    } catch (error) {
      logger.warn('Server logout failed, proceeding with local logout', error)
    } finally {
      // Clear all local data
      token.value = null
      refreshToken.value = null
      user.value = null
      loading.value = false
      error.value = null
      
      // Clear all possible stored tokens and data
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('vortex_user')

      logger.debug('All authentication data cleared from storage')
      
      // Redirect to React auth login
      redirectToAuth('login')
    }
  }

  async function checkAuth(): Promise<boolean> {
    if (!token.value) {
      return false
    }

    try {
      // Check if token is expired
      if (isTokenExpired(token.value)) {
        logger.info('Access token expired, attempting refresh')
        const tokenPayload = JSON.parse(atob(token.value.split('.')[1]))
        logger.debug('Token expiration details', { 
          expirationTime: new Date(tokenPayload.exp * 1000),
          currentTime: new Date()
        })
        const refreshed = await attemptTokenRefresh()
        if (!refreshed) {
          logger.warn('Token refresh failed, initiating logout')
          // Sem await, como antes: checkAuth responde false já, e o logout segue para o redirecionamento
          void logout()
          return false
        }
      }

      // If we have a valid token and user data, we're authenticated
      if (token.value && user.value) {
        return true
      }
      
      return false
    } catch (err: unknown) {
      logger.error('Authentication validation failed', err)
      return false
    }
  }

  // Single-flight: guard de rota, checkAuth e o interceptor 401 de api/config.ts
  // compartilham a mesma chamada a POST /api/auth/refresh enquanto ela estiver em andamento.
  // A chamada termina em até AUTH_REQUEST_TIMEOUT_MS (postToAuthService), então a promise
  // compartilhada sempre se resolve e a próxima renovação começa do zero.
  function attemptTokenRefresh(): Promise<boolean> {
    refreshInFlight ??= refreshTokens().finally(() => {
      refreshInFlight = null
    })
    return refreshInFlight
  }

  async function refreshTokens(): Promise<boolean> {
    // localStorage é a fonte de verdade dos tokens (o interceptor de api/config.ts lê de lá)
    const storedRefreshToken = localStorage.getItem('refreshToken') || refreshToken.value
    if (!storedRefreshToken) {
      return false
    }

    try {
      logger.debug('Initiating token refresh')
      const payload = await requestSessionRefresh(storedRefreshToken)

      // Update tokens and user
      token.value = payload.accessToken
      localStorage.setItem('accessToken', payload.accessToken)

      // Guardar o refresh token devolvido (hoje o serviço repete o mesmo; se passar a rotacionar, segue funcionando)
      if (payload.refreshToken) {
        refreshToken.value = payload.refreshToken
        localStorage.setItem('refreshToken', payload.refreshToken)
      }

      if (payload.user) {
        user.value = payload.user
        localStorage.setItem('vortex_user', JSON.stringify(payload.user))
      }

      logger.info('Token refresh completed successfully')
      return true
    } catch (error: unknown) {
      logger.error('Token refresh failed', error)
      
      // Clear invalid tokens
      token.value = null
      refreshToken.value = null
      user.value = null
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('vortex_user')
      
      return false
    }
  }

  async function initializeAuth(): Promise<boolean> {
    logger.debug('Initializing authentication store')
    // Try to load auth data from localStorage
    const accessToken = localStorage.getItem('accessToken')
    const refreshTokenValue = localStorage.getItem('refreshToken')
    const storedUser = localStorage.getItem('vortex_user')
    
    logger.debug('Stored auth data check', {
      hasAccessToken: !!accessToken,
      hasRefreshToken: !!refreshTokenValue,
      hasUserData: !!storedUser
    })
    
    if (accessToken) {
      logger.debug('Token validation details', {
        tokenLength: accessToken.length,
        tokenParts: accessToken.split('.').length
      })
    }
    
    if (!accessToken || !refreshTokenValue || !storedUser) {
      logger.debug('Authentication data incomplete, user not logged in')
      return false
    }
    
    try {
      token.value = accessToken
      refreshToken.value = refreshTokenValue
      user.value = JSON.parse(storedUser)
      logger.info('Authentication data loaded', { userEmail: user.value?.email })
      
      // Validate token
      const isValid = await checkAuth()
      logger.debug('Token validation completed', { isValid })
      
      if (!isValid) {
        // Clear invalid data
        token.value = null
        refreshToken.value = null
        user.value = null
        return false
      }
      
      return true
    } catch (e) {
      logger.error('Authentication initialization failed', e)
      return false
    }
  }

  return {
    user,
    token,
    refreshToken,
    loading,
    error,
    isAuthenticated,
    redirectToAuth,
    logout,
    checkAuth,
    initializeAuth,
    isTokenExpired,
    attemptTokenRefresh
  }
})