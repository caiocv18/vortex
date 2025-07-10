import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'
import { apiClient } from '@/api/config'

export interface User {
  id: number
  email: string
  name: string
  provider: string
  roles: string[]
  lastLogin: string
  emailVerified: boolean
}

export interface LoginResponse {
  token: string
  user: User
  tokenType: string
  expiresIn: number
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('vortex_auth_token'))
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


  function logout(): void {
    console.log('[Auth Store] Logging out...')
    token.value = null
    user.value = null
    localStorage.removeItem('vortex_auth_token')
    localStorage.removeItem('vortex_user')
    
    // Redirect to React auth login
    redirectToAuth('login')
  }

  async function checkAuth(): Promise<boolean> {
    if (!token.value) {
      return false
    }

    try {
      console.log('[Auth Store] Checking auth with token:', token.value.substring(0, 20) + '...')
      const response = await axios.get(
        'http://localhost:8081/auth/userinfo',
        {
          headers: {
            Authorization: `Bearer ${token.value}`
          }
        }
      )
      
      console.log('[Auth Store] Auth check response:', response.data)
      
      if (response.data) {
        // Update user info if needed
        user.value = {
          ...user.value!,
          ...response.data
        }
        return true
      }
      
      return false
    } catch (err: any) {
      console.error('[Auth Store] Error checking auth:', err.message)
      console.error('[Auth Store] Error details:', err.response?.data)
      
      // Don't logout on network errors, only on 401
      if (err.response?.status === 401) {
        logout()
      }
      return false
    }
  }

  async function initializeAuth(): Promise<boolean> {
    console.log('[Auth Store] Initializing auth...')
    // Try to load auth data from localStorage
    const authToken = localStorage.getItem('vortex_auth_token')
    const storedUser = localStorage.getItem('vortex_user')
    
    console.log('[Auth Store] Token found:', !!authToken)
    console.log('[Auth Store] User found:', !!storedUser)
    
    if (!authToken || !storedUser) {
      console.log('[Auth Store] Missing auth data')
      return false
    }
    
    try {
      token.value = authToken
      user.value = JSON.parse(storedUser)
      console.log('[Auth Store] Auth data loaded:', user.value.email)
      
      // Validate token with backend
      console.log('[Auth Store] Validating token with backend...')
      const isValid = await checkAuth()
      console.log('[Auth Store] Token validation result:', isValid)
      
      if (!isValid) {
        // Clear invalid data
        token.value = null
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
    loading,
    error,
    isAuthenticated,
    redirectToAuth,
    logout,
    checkAuth,
    initializeAuth
  }
})