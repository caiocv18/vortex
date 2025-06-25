import { defineStore } from 'pinia'

export const useThemeStore = defineStore('theme', {
  state: () => ({
    isDark: localStorage.getItem('theme') === 'dark'
  }),

  actions: {
    toggleTheme() {
      this.isDark = !this.isDark
      const theme = this.isDark ? 'dark' : 'light'
      localStorage.setItem('theme', theme)
      
      // Atualizar o tema do Vuetify usando a instância global
      if ((window as any).vuetify) {
        (window as any).vuetify.theme.global.name.value = theme
      }
    },

    initTheme() {
      const theme = localStorage.getItem('theme') || 'light'
      this.isDark = theme === 'dark'
      
      // Aplicar tema inicial usando a instância global
      if ((window as any).vuetify) {
        (window as any).vuetify.theme.global.name.value = theme
      }
    },

    setThemeInstance(theme: any) {
      // Não precisa mais dessa função
    }
  }
}) 