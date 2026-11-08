import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import vueDevTools from 'vite-plugin-vue-devtools'
import vuetify from 'vite-plugin-vuetify'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Vue DevTools só com VITE_DEVTOOLS=true (no shell ou num .env*): desligado por padrão,
  // porque o plugin pesa na memória do dev server.
  const env = loadEnv(mode, process.cwd(), '')
  const devtoolsEnabled = env.VITE_DEVTOOLS === 'true'

  return {
    plugins: [
      vue(),
      vueJsx(),
      // Tree-shaking do Vuetify: cada SFC importa só os componentes e diretivas que usa
      vuetify({ autoImport: true }),
      devtoolsEnabled && vueDevTools(),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      },
    },
    // Guia de upgrade do Vuetify 4 ("overlay z-index in dev mode"): pré-empacotar os módulos
    // de overlay juntos, senão menus e selects abertos dentro de v-dialog ficam atrás dele no dev.
    optimizeDeps: {
      include: [
        'vuetify/components/VOverlay',
        'vuetify/components/VDialog',
        'vuetify/components/VMenu',
        'vuetify/components/VSelect',
        'vuetify/components/VTooltip',
      ],
    },
    server: {
      port: 5173
    }
  }
})
