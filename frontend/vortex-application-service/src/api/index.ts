import { 
  TiposDeProdutoApi, 
  ProdutosApi, 
  MovimentosDeEstoqueApi, 
  RelatriosApi 
} from './generated'
import { apiClient, apiConfig } from './config'

// Instâncias das APIs
export const tiposProdutoApi = new TiposDeProdutoApi(apiConfig, undefined, apiClient)
export const produtosApi = new ProdutosApi(apiConfig, undefined, apiClient)
export const movimentosApi = new MovimentosDeEstoqueApi(apiConfig, undefined, apiClient)
export const relatoriosApi = new RelatriosApi(apiConfig, undefined, apiClient)

// Exportar tipos
export * from './generated' 