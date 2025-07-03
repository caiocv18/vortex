import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { title: 'Home' }
    },
    {
      path: '/product-types',
      name: 'productTypes',
      component: () => import('../views/ProductTypesView.vue'),
      meta: { title: 'Tipos de Produto' }
    },
    {
      path: '/products',
      name: 'products',
      component: () => import('../views/ProductsView.vue'),
      meta: { title: 'Produtos' }
    },
    {
      path: '/movements',
      name: 'movements',
      component: () => import('../views/MovementsView.vue'),
      meta: { title: 'Movimentação' }
    },
    {
      path: '/reports',
      name: 'reports',
      component: () => import('../views/ReportsView.vue'),
      meta: { title: 'Relatórios' }
    }
  ]
})

// Atualizar título da página
router.beforeEach((to, from, next) => {
  document.title = `${to.meta.title || 'Vortex'} - Sistema de Estoque`
  next()
})

export default router
