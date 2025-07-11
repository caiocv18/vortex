import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'
import { apiClient } from '@/api/config'

export interface User {
  id: string
  email: string
  username: string
  roles: string[]
  lastLogin?: string
  isActive: boolean
  isVerified: boolean
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: User
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('accessToken'))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const user = ref<User | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const isAuthenticated = computed(() => !!token.value && !!user.value)

  // Initialize user from localStorage
  const storedUser = localStorage.getItem('vortex_user')
  if (storedUser) {
    try {
      user.value = JSON.parse(storedUser)
    } catch (e) {
      console.error('Error parsing stored user:', e)
    }
  }

  // Note: apiClient interceptors are already set up in api/config.ts
  // No need to duplicate them here

  function redirectToAuth(action: 'login' | 'register' = 'login'): void {
    console.log('[Auth Store] Redirecting to auth service:', action)
    // Save current URL to return after authentication
    const returnUrl = window.location.href
    sessionStorage.setItem('vortex_return_url', returnUrl)
    console.log('[Auth Store] Saved return URL:', returnUrl)
    
    // Redirect to React auth frontend
    window.location.href = `http://localhost:3001/${action}`
  }


  async function logout(): Promise<void> {
    console.log('[Auth Store] Logging out...')
    
    try {
      // Call logout endpoint if refresh token exists
      if (refreshToken.value) {
        await axios.post('http://localhost:8081/api/auth/logout', {
          refreshToken: refreshToken.value
        })
      }
    } catch (error) {
      console.error('Error during logout:', error)
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
      sessionStorage.removeItem('vortex_return_url')
      
      console.log('[Auth Store] All auth data cleared')
      
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
        console.log('[Auth Store] Token expired, attempting refresh...')
        console.log('[Auth Store] Token expiration time:', new Date(JSON.parse(atob(token.value.split('.')[1])).exp * 1000))
        console.log('[Auth Store] Current time:', new Date())
        const refreshed = await attemptTokenRefresh()
        if (!refreshed) {
          console.log('[Auth Store] Token refresh failed, logging out')
          logout()
          return false
        }
      }

      // If we have a valid token and user data, we're authenticated
      if (token.value && user.value) {
        return true
      }
      
      return false
    } catch (err: any) {
      console.error('[Auth Store] Error checking auth:', err.message)
      return false
    }
  }

  function isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]))
      const currentTime = Date.now() / 1000
      return payload.exp < currentTime
    } catch {
      return true
    }
  }

  async function attemptTokenRefresh(): Promise<boolean> {
    if (!refreshToken.value) {
      return false
    }

    try {
      console.log('[Auth Store] Refreshing token...')
      const response = await axios.post('http://localhost:8081/api/auth/refresh', {
        refreshToken: refreshToken.value
      })

      const { accessToken, user: userData } = response.data.data
      
      // Update tokens and user
      token.value = accessToken
      user.value = userData
      
      // Update localStorage
      localStorage.setItem('accessToken', accessToken)
      localStorage.setItem('vortex_user', JSON.stringify(userData))
      
      console.log('[Auth Store] Token refreshed successfully')
      return true
    } catch (error: any) {
      console.error('[Auth Store] Token refresh failed:', error)
      
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
    console.log('[Auth Store] Initializing auth...')
    // Try to load auth data from localStorage
    const accessToken = localStorage.getItem('accessToken')
    const refreshTokenValue = localStorage.getItem('refreshToken')
    const storedUser = localStorage.getItem('vortex_user')
    
    console.log('[Auth Store] Access token found:', !!accessToken)
    console.log('[Auth Store] Access token preview:', accessToken ? `${accessToken.substring(0, 20)}...` : 'No token')
    console.log('[Auth Store] Refresh token found:', !!refreshTokenValue)
    console.log('[Auth Store] User found:', !!storedUser)
    
    if (accessToken) {
      console.log('[Auth Store] Token length:', accessToken.length)
      console.log('[Auth Store] Token parts:', accessToken.split('.').length)
    }
    
    if (!accessToken || !refreshTokenValue || !storedUser) {
      console.log('[Auth Store] Missing auth data')
      return false
    }
    
    try {
      token.value = accessToken
      refreshToken.value = refreshTokenValue
      user.value = JSON.parse(storedUser)
      console.log('[Auth Store] Auth data loaded:', user.value?.email)
      
      // Validate token
      const isValid = await checkAuth()
      console.log('[Auth Store] Token validation result:', isValid)
      
      if (!isValid) {
        // Clear invalid data
        token.value = null
        refreshToken.value = null
        user.value = null
        return false
      }
      
      return true
    } catch (e) {
      console.error('[Auth Store] Error initializing auth:', e)
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