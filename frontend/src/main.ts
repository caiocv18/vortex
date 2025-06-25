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

// Custom theme
const nexdomTheme = {
  dark: false,
  colors: {
    primary: '#59cb9b',
    secondary: '#00262c',
    background: '#f5ffff',
    surface: '#FFFFFF',
    error: '#B00020',
    info: '#2196F3',
    success: '#4CAF50',
    warning: '#FB8C00',
  }
}

const nexdomThemeDark = {
  dark: true,
  colors: {
    primary: '#59cb9b',
    secondary: '#00262c',
    background: '#121212',
    surface: '#1E1E1E',
    error: '#CF6679',
    info: '#2196F3',
    success: '#4CAF50',
    warning: '#FB8C00',
  }
}

const savedTheme = localStorage.getItem('theme') || 'light'

const vuetify = createVuetify({
  components,
  directives,
  theme: {
    defaultTheme: savedTheme,
    themes: {
      light: nexdomTheme,
      dark: nexdomThemeDark,
    },
  },
})

// Tornar vuetify acessível globalmente para o tema
;(window as any).vuetify = vuetify

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(vuetify)

app.mount('#app')
