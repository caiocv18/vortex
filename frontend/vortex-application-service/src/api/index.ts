import { 
  TiposDeProdutoApi, 
  ProdutosApi, 
  MovimentosDeEstoqueApi, 
  RelatriosApi 
} from './generated'
import { apiClient, apiConfig } from './config'

// Debug: Verificar configuração
console.log('🔧 [API Setup] Initializing API instances with:', {
  baseURL: apiConfig.basePath,
  hasAxiosInstance: !!apiClient,
  axiosDefaults: apiClient.defaults
})

// Instâncias das APIs
export const tiposProdutoApi = new TiposDeProdutoApi(apiConfig, undefined, apiClient)
export const produtosApi = new ProdutosApi(apiConfig, undefined, apiClient)
export const movimentosApi = new MovimentosDeEstoqueApi(apiConfig, undefined, apiClient)
export const relatoriosApi = new RelatriosApi(apiConfig, undefined, apiClient)

// Exportar tipos
export * from './generated' 