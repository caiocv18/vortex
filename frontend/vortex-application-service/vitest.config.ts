import { fileURLToPath } from 'node:url'
import { mergeConfig, defineConfig, configDefaults } from 'vitest/config'
import viteConfig from './vite.config.ts'

// vite.config.ts exporta uma função (lê VITE_DEVTOOLS por modo), então o merge recebe o configEnv
export default defineConfig((configEnv) =>
  mergeConfig(
    viteConfig(configEnv),
    defineConfig({
      test: {
        environment: 'jsdom',
        exclude: [...configDefaults.exclude, 'e2e/**'],
        root: fileURLToPath(new URL('./', import.meta.url)),
        // Teste que monta componente do Vuetify (App.spec.ts): os componentes do pacote importam o
        // próprio .css, que o Node não carrega ("Unknown file extension .css"). Inline faz o Vite
        // processar o vuetify, como no guia de testes unitários do Vuetify.
        server: {
          deps: {
            inline: ['vuetify'],
          },
        },
        // Cobertura só com --coverage (job sonar-fronts do sonar.yml). O lcov sai com caminhos relativos à
        // raiz do repositório (projectRoot), que é onde o SonarCloud resolve os arquivos do relatório
        coverage: {
          provider: 'v8',
          reporter: [
            'text-summary',
            ['lcov', { projectRoot: fileURLToPath(new URL('../..', import.meta.url)) }],
          ],
        },
      },
    }),
  ),
)
