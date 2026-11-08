import { defineStore } from 'pinia'

type ThemeName = 'light' | 'dark'

// Aplica o tema na instância global do Vuetify (window.vuetify, exposta em main.ts).
// Vuetify 4: a troca é por theme.change(); atribuir theme.global.name.value deixou de ser a API.
function applyVuetifyTheme(theme: ThemeName) {
  window.vuetify?.theme.change(theme).catch((error: unknown) => {
    console.error('[Theme] Falha ao aplicar o tema no Vuetify:', error)
  })
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    isDark: localStorage.getItem('theme') === 'dark'
  }),

  actions: {
    toggleTheme() {
      this.isDark = !this.isDark
      const theme: ThemeName = this.isDark ? 'dark' : 'light'
      localStorage.setItem('theme', theme)

      // Atualizar o tema do Vuetify usando a instância global
      applyVuetifyTheme(theme)
    },

    initTheme() {
      const theme: ThemeName = localStorage.getItem('theme') === 'dark' ? 'dark' : 'light'
      this.isDark = theme === 'dark'

      // Aplicar tema inicial usando a instância global
      applyVuetifyTheme(theme)
    }
  }
})
