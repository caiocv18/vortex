import axios from 'axios'
import { Configuration } from './generated'

// Configuração base do axios
// Detectar automaticamente o ambiente
const getApiBaseUrl = () => {
  // Se variável de ambiente estiver definida, usar ela
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL
  }
  
  // Se estivermos rodando no Docker (porta 3000) ou em produção, usar URL relativa
  if (window.location.port === '3000' || 
      (import.meta.env.PROD && !window.location.hostname.includes('localhost'))) {
    return window.location.origin
  }
  
  // Padrão para desenvolvimento local
  return 'http://localhost:8080'
}

const baseURL = getApiBaseUrl()

console.log('API Base URL:', baseURL)

export const apiClient = axios.create({
  baseURL: baseURL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  }
})

// Interceptor para adicionar token (se necessário no futuro)
apiClient.interceptors.request.use(
  (config) => {
    // Log das requisições para debug
    console.log('API Request:', {
      method: config.method,
      url: config.url,
      baseURL: config.baseURL,
      fullURL: `${config.baseURL}${config.url}`
    })
    return config
  },
  (error) => {
    console.error('Request Error:', error)
    return Promise.reject(error)
  }
)

// Interceptor para tratamento de erros
apiClient.interceptors.response.use(
  (response) => {
    // Log de sucesso
    console.log('API Response:', {
      url: response.config.url,
      status: response.status,
      data: response.data
    })
    return response
  },
  (error) => {
    if (error.response) {
      // Erro de resposta do servidor
      console.error('API Error:', {
        url: error.config?.url,
        status: error.response.status,
        data: error.response.data
      })
    } else if (error.request) {
      // Sem resposta do servidor
      console.error('Network Error:', error.message)
    } else {
      // Erro ao configurar a requisição
      console.error('Request Error:', error.message)
    }
    return Promise.reject(error)
  }
)

// Configuração para as APIs geradas
export const apiConfig = new Configuration({
  basePath: baseURL
}) 