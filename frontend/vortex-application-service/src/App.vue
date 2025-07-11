<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useTheme } from 'vuetify'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import { RouterView, useRoute } from 'vue-router'

const drawer = ref(true)
const rail = ref(false)
const theme = useTheme()
const themeStore = useThemeStore()
const authStore = useAuthStore()
const route = useRoute()

const menuItems = [
  { title: 'Home', value: 'home', icon: 'mdi-home', to: '/' },
  { title: 'Tipos de Produto', value: 'productTypes', icon: 'mdi-shape', to: '/product-types' },
  { title: 'Produtos', value: 'products', icon: 'mdi-package-variant', to: '/products' },
  { title: 'Movimentação', value: 'movements', icon: 'mdi-swap-horizontal', to: '/movements' },
  { title: 'Relatórios', value: 'reports', icon: 'mdi-chart-box', to: '/reports' }
]

const toggleTheme = () => {
  themeStore.toggleTheme()
}

const handleLogout = () => {
  authStore.logout()
}

const showNavigation = computed(() => {
  // Always show navigation when authenticated
  return authStore.isAuthenticated
})

const userAvatar = computed(() => {
  const username = authStore.user?.username || authStore.user?.email || ''
  return username.charAt(0).toUpperCase()
})

onMounted(async () => {
  themeStore.setThemeInstance(theme)
  themeStore.initTheme()
  
  // Initialize auth store if user data exists in localStorage
  const hasTokens = localStorage.getItem('accessToken') && localStorage.getItem('refreshToken')
  if (hasTokens && !authStore.isAuthenticated) {
    console.log('[App] Initializing auth store from localStorage')
    const initialized = await authStore.initializeAuth()
    if (!initialized) {
      console.log('[App] Auth initialization failed')
    }
  }
})
</script>

<template>
  <v-app>
    <!-- Navigation Drawer -->
    <v-navigation-drawer
      v-if="showNavigation"
      v-model="drawer"
      :rail="rail"
      permanent
      @click="rail = false"
      app
    >
      <v-list-item
        prepend-avatar="/logo.png"
        title="Sistema de Estoque"
        subtitle="Vortex"
      >
        <template v-slot:append>
          <v-btn
            variant="text"
            icon="mdi-chevron-left"
            @click.stop="rail = !rail"
          ></v-btn>
        </template>
      </v-list-item>

      <v-divider></v-divider>

      <v-list density="compact" nav>
        <v-list-item
          v-for="item in menuItems"
          :key="item.value"
          :value="item.value"
          :to="item.to"
          :prepend-icon="item.icon"
          :title="item.title"
          color="primary"
        ></v-list-item>
      </v-list>

      <template v-slot:append>
        <v-divider></v-divider>
        <v-list density="compact" nav>
          <v-list-item
            :prepend-icon="themeStore.isDark ? 'mdi-weather-night' : 'mdi-weather-sunny'"
            :title="themeStore.isDark ? 'Tema Escuro' : 'Tema Claro'"
            @click="toggleTheme"
          ></v-list-item>
          <v-list-item
            prepend-icon="mdi-logout"
            title="Sair"
            @click="handleLogout"
          ></v-list-item>
        </v-list>
      </template>
    </v-navigation-drawer>

    <!-- App Bar -->
    <v-app-bar
      v-if="showNavigation"
      flat
      color="primary"
      app
    >
      <v-app-bar-nav-icon
        @click="drawer = !drawer"
        v-if="!drawer || rail"
      ></v-app-bar-nav-icon>

      <v-toolbar-title>Sistema de Estoque</v-toolbar-title>

      <v-spacer></v-spacer>

      <!-- User menu -->
      <v-menu
        v-if="authStore.isAuthenticated"
        offset-y
        :close-on-content-click="false"
      >
        <template v-slot:activator="{ props }">
          <v-btn
            v-bind="props"
            icon
            class="mx-2"
          >
            <v-avatar color="secondary" size="36">
              <span class="white--text text-h6">{{ userAvatar }}</span>
            </v-avatar>
          </v-btn>
        </template>
        <v-card min-width="250">
          <v-card-text>
            <div class="text-center mb-2">
              <v-avatar color="secondary" size="64">
                <span class="white--text text-h4">{{ userAvatar }}</span>
              </v-avatar>
            </div>
            <p class="text-h6 text-center mb-1">{{ authStore.user?.username }}</p>
            <p class="text-body-2 text-center text-grey">{{ authStore.user?.email }}</p>
          </v-card-text>
          <v-divider></v-divider>
          <v-card-actions>
            <v-btn
              block
              variant="text"
              color="error"
              @click="handleLogout"
            >
              <v-icon left>mdi-logout</v-icon>
              Sair
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-menu>

      <v-btn icon @click="toggleTheme">
        <v-icon>{{ themeStore.isDark ? 'mdi-weather-night' : 'mdi-weather-sunny' }}</v-icon>
      </v-btn>
    </v-app-bar>

    <!-- Main Content -->
    <v-main>
      <v-container fluid class="fill-height pa-0">
        <RouterView v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </RouterView>
      </v-container>
    </v-main>
  </v-app>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.fill-height {
  height: 100%;
}
</style>
