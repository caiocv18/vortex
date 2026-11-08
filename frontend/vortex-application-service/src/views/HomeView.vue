<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { tiposProdutoApi, type TipoProdutoDTO } from '@/api'

interface ApiTestError {
  message: string
  response?: unknown
  code?: string
}

// Teste de API
const apiTestResult = ref<TipoProdutoDTO[] | null>(null)
const apiTestError = ref<ApiTestError | null>(null)
const loading = ref(false)

const testApi = async () => {
  loading.value = true
  apiTestResult.value = null
  apiTestError.value = null
  
  try {
    console.log('Testando API com token...')
    const response = await tiposProdutoApi.findAllTiposProduto()
    console.log('Resposta do teste:', response)
    // O spec OpenAPI tipa a lista como objeto único; normaliza como as stores fazem
    apiTestResult.value = Array.isArray(response.data) ? response.data : [response.data]
  } catch (error: unknown) {
    console.error('Erro no teste:', error)
    apiTestError.value = {
      message: error instanceof Error ? error.message : String(error),
      response: axios.isAxiosError(error) ? error.response : undefined,
      code: axios.isAxiosError(error) ? error.code : undefined
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  testApi()
})
</script>

<template>
  <!-- Espaçamento vertical: só o gap do grid do Vuetify 4 (24px entre linhas e colunas); margens
       extras nos cards e nas linhas somavam com ele e davam 48px num lugar e 24px no outro -->
  <v-container fluid class="pa-4">
    <v-row>
      <v-col cols="12">
        <v-card>
          <v-card-title class="text-headline-large text-wrap">
            Bem-vindo ao Sistema de Estoque
          </v-card-title>
          <v-card-subtitle>
            Sistema de gerenciamento de estoque Vortex
          </v-card-subtitle>
        </v-card>
      </v-col>
    </v-row>

    <!-- Card de Teste de API -->
    <v-row>
      <v-col cols="12">
        <v-card>
          <!-- flex-wrap: em tela estreita o botão desce para a linha de baixo em vez de cortar o título -->
          <v-card-title class="d-flex flex-wrap align-center ga-4 text-wrap">
            Teste de Conexão com API
            <v-btn
              @click="testApi"
              size="small"
              variant="outlined"
              :loading="loading"
            >
              Testar Novamente
            </v-btn>
          </v-card-title>
          <v-card-text>
            <div v-if="loading">
              <v-progress-circular indeterminate></v-progress-circular>
              <span class="ml-2">Testando conexão...</span>
            </div>
            
            <div v-else-if="apiTestResult">
              <v-alert type="success" variant="tonal" class="mb-4">
                Conexão com API bem-sucedida!
              </v-alert>
              <p><strong>Total de tipos de produto:</strong> {{ apiTestResult.length }}</p>
              <v-expansion-panels class="mt-4">
                <v-expansion-panel>
                  <v-expansion-panel-title>
                    Ver dados recebidos
                  </v-expansion-panel-title>
                  <v-expansion-panel-text>
                    <pre>{{ JSON.stringify(apiTestResult, null, 2) }}</pre>
                  </v-expansion-panel-text>
                </v-expansion-panel>
              </v-expansion-panels>
            </div>
            
            <div v-else-if="apiTestError">
              <v-alert type="error" variant="tonal" class="mb-4">
                Erro ao conectar com a API
              </v-alert>
              <p><strong>Mensagem:</strong> {{ apiTestError.message }}</p>
              <p><strong>Código:</strong> {{ apiTestError.code }}</p>
              <v-expansion-panels class="mt-4">
                <v-expansion-panel>
                  <v-expansion-panel-title>
                    Ver detalhes do erro
                  </v-expansion-panel-title>
                  <v-expansion-panel-text>
                    <pre>{{ JSON.stringify(apiTestError, null, 2) }}</pre>
                  </v-expansion-panel-text>
                </v-expansion-panel>
              </v-expansion-panels>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <!-- Cards da mesma linha com a mesma altura: o v-col já estica (flex), o card ocupa a altura
         toda (h-100) e o "Acessar" vai para o pé do card (mt-auto), alinhado entre eles -->
    <v-row>
      <v-col cols="12" md="6" lg="3">
        <v-card class="h-100 d-flex flex-column">
          <v-card-title class="d-flex align-center text-wrap">
            <v-icon size="40" color="primary" class="mr-3">mdi-shape</v-icon>
            <div>
              <div class="text-title-large">Tipos de Produto</div>
              <div class="text-body-small">Categorias disponíveis</div>
            </div>
          </v-card-title>
          <v-card-text>
            <p>Gerencie os tipos de produtos do seu estoque.</p>
          </v-card-text>
          <v-card-actions class="mt-auto">
            <v-btn color="primary" to="/product-types" variant="text">
              Acessar
              <v-icon end>mdi-arrow-right</v-icon>
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-col>

      <v-col cols="12" md="6" lg="3">
        <v-card class="h-100 d-flex flex-column">
          <v-card-title class="d-flex align-center text-wrap">
            <v-icon size="40" color="primary" class="mr-3">mdi-package-variant</v-icon>
            <div>
              <div class="text-title-large">Produtos</div>
              <div class="text-body-small">Itens em estoque</div>
            </div>
          </v-card-title>
          <v-card-text>
            <p>Cadastre e gerencie todos os produtos.</p>
          </v-card-text>
          <v-card-actions class="mt-auto">
            <v-btn color="primary" to="/products" variant="text">
              Acessar
              <v-icon end>mdi-arrow-right</v-icon>
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-col>

      <v-col cols="12" md="6" lg="3">
        <v-card class="h-100 d-flex flex-column">
          <v-card-title class="d-flex align-center text-wrap">
            <v-icon size="40" color="primary" class="mr-3">mdi-swap-horizontal</v-icon>
            <div>
              <div class="text-title-large">Movimentação</div>
              <div class="text-body-small">Entradas e saídas</div>
            </div>
          </v-card-title>
          <v-card-text>
            <p>Registre movimentações de estoque.</p>
          </v-card-text>
          <v-card-actions class="mt-auto">
            <v-btn color="primary" to="/movements" variant="text">
              Acessar
              <v-icon end>mdi-arrow-right</v-icon>
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-col>

      <v-col cols="12" md="6" lg="3">
        <v-card class="h-100 d-flex flex-column">
          <v-card-title class="d-flex align-center text-wrap">
            <v-icon size="40" color="primary" class="mr-3">mdi-chart-box</v-icon>
            <div>
              <div class="text-title-large">Relatórios</div>
              <div class="text-body-small">Análises e métricas</div>
            </div>
          </v-card-title>
          <v-card-text>
            <p>Visualize relatórios detalhados.</p>
          </v-card-text>
          <v-card-actions class="mt-auto">
            <v-btn color="primary" to="/reports" variant="text">
              Acessar
              <v-icon end>mdi-arrow-right</v-icon>
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>
