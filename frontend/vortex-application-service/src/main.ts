import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

// Vuetify
import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import '@mdi/font/css/materialdesignicons.css'

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
  components,
  directives,
  theme: {
    defaultTheme: savedTheme,
    themes: {
      light: vortexTheme,
      dark: vortexThemeDark,
    },
  },
})

// Tornar vuetify acessível globalmente para o tema
;(window as any).vuetify = vuetify

// Handle auth callback before app initialization
handleAuthCallback()

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(vuetify)

app.mount('#app')
