/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_AUTH_BACKEND_URL?: string
  readonly VITE_AUTH_SERVICE_URL?: string
  readonly VITE_THEME_COLOR?: string
  readonly VITE_LOG_LEVEL?: string
}

interface Window {
  // Instância do Vuetify exposta em src/main.ts (usada pela store de tema)
  vuetify?: ReturnType<typeof import('vuetify')['createVuetify']>
}
