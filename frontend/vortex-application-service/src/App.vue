<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useDisplay } from 'vuetify'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import { RouterView } from 'vue-router'

// Drawer responsivo, pelos breakpoints padrão do Vuetify 4 (mobile = largura abaixo de lg, 1145px;
// cobre celular, tablet e a faixa sm/md de 600 a 1145px):
// - desktop: fixo (permanent), aberto, com o modo rail, como sempre foi;
// - mobile: temporário (sobreposto ao conteúdo, com scrim), fechado ao carregar, aberto pelo
//   ícone do app bar; o próprio VNavigationDrawer fecha ao navegar, ao tocar no scrim e ao
//   passar de desktop para mobile (e reabre ao voltar para desktop, por ser permanent lá).
const { mobile, xs } = useDisplay()
const drawer = ref(!mobile.value)
const rail = ref(false)
// Rail só no desktop: no drawer temporário ele esconderia os rótulos sem ganhar espaço nenhum
const railActive = computed(() => rail.value && !mobile.value)
const themeStore = useThemeStore()
const authStore = useAuthStore()

// Botão do cabeçalho do drawer: no desktop alterna o rail; no mobile fecha o drawer
const onDrawerHeaderToggle = () => {
  if (mobile.value) {
    drawer.value = false
  } else {
    rail.value = !rail.value
  }
}

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
      :rail="railActive"
      :permanent="!mobile"
      @click="rail = false"
      app
    >
      <!-- Título em title-small e com quebra de linha: no tamanho padrão do Material 3 o nome
           não cabe nos 256px (avatar + botão do rail) e virava "Sistema de E..." -->
      <v-list-item prepend-avatar="/logo.png">
        <v-list-item-title class="text-title-small text-wrap">Sistema de Estoque</v-list-item-title>
        <v-list-item-subtitle>Vortex</v-list-item-subtitle>
        <template v-slot:append>
          <v-btn
            variant="text"
            icon="mdi-chevron-left"
            @click.stop="onDrawerHeaderToggle"
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
      <!-- No mobile o ícone fica sempre visível: é ele que abre e fecha o drawer temporário -->
      <v-app-bar-nav-icon
        @click="drawer = !drawer"
        v-if="mobile || !drawer || rail"
      ></v-app-bar-nav-icon>

      <!-- Sem v-spacer: no Vuetify 4 o v-toolbar-title já cresce (flex: 1 1) e empurra os botões
           para a direita; com o spacer os dois dividiam o espaço livre ao meio e, no celular, o
           título ficava com metade da largura e cortava. No xs (abaixo de 600px) o título desce
           para title-medium para caber inteiro ao lado dos três botões. -->
      <v-toolbar-title :class="{ 'text-title-medium': xs }">Sistema de Estoque</v-toolbar-title>

      <!-- User menu -->
      <v-menu
        v-if="authStore.isAuthenticated"
        :close-on-content-click="false"
      >
        <template v-slot:activator="{ props }">
          <v-btn
            v-bind="props"
            icon
            class="mx-2"
          >
            <v-avatar color="secondary" size="36">
              <span class="text-white text-title-large">{{ userAvatar }}</span>
            </v-avatar>
          </v-btn>
        </template>
        <v-card min-width="250">
          <v-card-text>
            <div class="text-center mb-2">
              <v-avatar color="secondary" size="64">
                <span class="text-white text-headline-large">{{ userAvatar }}</span>
              </v-avatar>
            </div>
            <p class="text-title-large text-center mb-1">{{ authStore.user?.username }}</p>
            <p class="text-body-medium text-center text-grey">{{ authStore.user?.email }}</p>
          </v-card-text>
          <v-divider></v-divider>
          <v-card-actions>
            <v-btn
              block
              variant="text"
              color="error"
              @click="handleLogout"
            >
              <v-icon start>mdi-logout</v-icon>
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
