// Vuetify 4: a ordem das @layer é declarada em public/layers.css (linkado no index.html).
// 'vuetify/styles' continua antes do CSS da app. Componentes e diretivas entram sob
// demanda pelo vite-plugin-vuetify (autoImport), sem import * de tudo.
import 'vuetify/styles'
import '@mdi/font/css/materialdesignicons.css'
import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createVuetify } from 'vuetify'

import App from './App.vue'
import router from './router'
import { handleAuthCallback } from './utils/authCallback'

// Import test utilities (for debugging)
import './test-api'

// Import theme system
import { getThemeByName, defaultTheme } from './themes/colorThemes'

// Get selected theme from environment or use default
const selectedTheme = import.meta.env.VITE_THEME_COLOR || defaultTheme
const themeConfig = getThemeByName(selectedTheme)

// Fallback to default if theme not found
const finalTheme = themeConfig || getThemeByName(defaultTheme)!

console.log(`🎨 [Theme] Using ${finalTheme.displayName} theme (${selectedTheme})`)

// Custom theme based on selection
const vortexTheme = {
  dark: false,
  colors: finalTheme.light
}

const vortexThemeDark = {
  dark: true,
  colors: finalTheme.dark
}

const savedTheme = localStorage.getItem('theme') || 'light'

const vuetify = createVuetify({
  theme: {
    // Explícito: no Vuetify 4 o padrão passou a ser 'system'
    defaultTheme: savedTheme,
    themes: {
      light: vortexTheme,
      dark: vortexThemeDark,
    },
  },
})

// Tornar vuetify acessível globalmente para o tema (tipado em env.d.ts)
window.vuetify = vuetify

// Handle auth callback before app initialization
handleAuthCallback()

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(vuetify)

app.mount('#app')
