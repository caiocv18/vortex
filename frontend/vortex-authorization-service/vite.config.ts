import { fileURLToPath, URL } from 'node:url'
import { loadEnv } from 'vite'
// defineConfig do vitest/config: o mesmo do Vite, tipado também com a chave `test`
import { configDefaults, defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const projectRoot = fileURLToPath(new URL('./', import.meta.url))

export default defineConfig(({ mode }) => {
  // Source maps: ligados em todo modo que não seja 'production' (dev server, vite build --mode development).
  // No build de produção, o padrão de `vite build` e o que roda no Dockerfile e no start-vortex.sh (prd),
  // ficam desligados para o dist sair leve. Para religar num build de produção:
  // VITE_SOURCEMAP=true npm run build (vale também como linha num .env* do módulo).
  const env = loadEnv(mode, projectRoot, '')
  const sourcemap = mode !== 'production' || env.VITE_SOURCEMAP === 'true'

  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 3001,
      host: true,
      cors: true
    },
    preview: {
      port: 3001,
      host: true
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    build: {
      outDir: 'dist',
      sourcemap
    },
    // Vitest (npm run test): jsdom + Testing Library; e2e/ fica para o Playwright
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      exclude: [...configDefaults.exclude, 'e2e/**']
    }
  }
})
