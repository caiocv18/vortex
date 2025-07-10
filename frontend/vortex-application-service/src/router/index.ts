import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { title: 'Home', requiresAuth: true }
    },
    {
      path: '/product-types',
      name: 'productTypes',
      component: () => import('../views/ProductTypesView.vue'),
      meta: { title: 'Tipos de Produto', requiresAuth: true }
    },
    {
      path: '/products',
      name: 'products',
      component: () => import('../views/ProductsView.vue'),
      meta: { title: 'Produtos', requiresAuth: true }
    },
    {
      path: '/movements',
      name: 'movements',
      component: () => import('../views/MovementsView.vue'),
      meta: { title: 'Movimentação', requiresAuth: true }
    },
    {
      path: '/reports',
      name: 'reports',
      component: () => import('../views/ReportsView.vue'),
      meta: { title: 'Relatórios', requiresAuth: true }
    }
  ]
})

// Route guard for authentication
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  
  console.log('[Router] Navigation to:', to.path)
  console.log('[Router] From:', from.path)
  
  // Update page title
  document.title = `${to.meta.title || 'Vortex'} - Sistema de Estoque`
  
  // Check if route requires authentication
  const requiresAuth = to.meta.requiresAuth !== false
  
  if (requiresAuth) {
    // First, check if we have a stored token
    const hasAuthToken = localStorage.getItem('vortex_auth_token')
    console.log('[Router] Has auth token:', !!hasAuthToken)
    console.log('[Router] Is authenticated:', authStore.isAuthenticated)
    
    if (hasAuthToken) {
      // Try to initialize auth store if not already authenticated
      if (!authStore.isAuthenticated) {
        console.log('[Router] Initializing auth...')
        const initialized = await authStore.initializeAuth()
        console.log('[Router] Auth initialized:', initialized)
        if (!initialized) {
          // Clear the bad token
          localStorage.removeItem('vortex_auth_token')
          localStorage.removeItem('vortex_user')
          
          // Token is invalid, redirect to auth
          console.log('[Router] Token invalid, redirecting to login')
          authStore.redirectToAuth('login')
          return false
        }
      }
    } else {
      // No token, redirect to auth
      console.log('[Router] No token found, redirecting to login')
      authStore.redirectToAuth('login')
      return false
    }
  }
  
  next()
})

export default router
