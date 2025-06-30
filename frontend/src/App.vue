<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useTheme } from 'vuetify'
import { useThemeStore } from '@/stores/theme'
import { RouterView } from 'vue-router'

const drawer = ref(true)
const rail = ref(false)
const theme = useTheme()
const themeStore = useThemeStore()

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

onMounted(() => {
  themeStore.setThemeInstance(theme)
  themeStore.initTheme()
})
</script>

<template>
  <v-app>
    <!-- Navigation Drawer -->
    <v-navigation-drawer
      v-model="drawer"
      :rail="rail"
      permanent
      @click="rail = false"
      app
    >
      <v-list-item
        prepend-avatar="/logo.png"
        title="Sistema de Estoque"
        subtitle="Nexdom"
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
        </v-list>
      </template>
    </v-navigation-drawer>

    <!-- App Bar -->
    <v-app-bar
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
