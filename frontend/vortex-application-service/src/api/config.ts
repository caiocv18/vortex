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
  },
  // Garantir que o axios use as credenciais
  withCredentials: false
})

// Interceptor para adicionar token
apiClient.interceptors.request.use(
  (config) => {
    // Adicionar token de autenticação se disponível
    const token = localStorage.getItem('accessToken')
    console.log('🔐 [API Request] Token check:', {
      hasToken: !!token,
      tokenPreview: token ? `${token.substring(0, 20)}...` : 'No token',
      tokenLength: token ? token.length : 0
    })
    
    if (token) {
      // Garantir que headers existe
      if (!config.headers) {
        config.headers = {} as any
      }
      config.headers.Authorization = `Bearer ${token}`
    }
    
    // Log das requisições para debug
    console.log('🚀 [API Request] Details:', {
      method: config.method,
      url: config.url,
      baseURL: config.baseURL,
      fullURL: `${config.baseURL}${config.url}`,
      hasAuth: !!config.headers?.Authorization,
      authHeader: config.headers?.Authorization ? 'Bearer ***' : 'None',
      allHeaders: config.headers
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
    console.log('✅ [API Response] Success:', {
      url: response.config.url,
      status: response.status,
      hasData: !!response.data
    })
    return response
  },
  (error) => {
    if (error.response) {
      // Erro de resposta do servidor
      console.error('❌ [API Error] Server response:', {
        url: error.config?.url,
        status: error.response.status,
        data: error.response.data,
        headers: error.config?.headers
      })
      
      // Se for erro 401 (não autorizado), redirecionar para login
      if (error.response.status === 401) {
        console.log('🔐 [API Error] Unauthorized access, redirecting to login...')
        // Limpar token inválido
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
        // Redirecionar para login
        const loginUrl = 'http://localhost:3001/login'
        const returnUrl = encodeURIComponent(window.location.href)
        window.location.href = `${loginUrl}?returnUrl=${returnUrl}`
      }
    } else if (error.request) {
      // Sem resposta do servidor
      console.error('❌ [API Error] Network error:', error.message)
    } else {
      // Erro ao configurar a requisição
      console.error('❌ [API Error] Request setup error:', error.message)
    }
    return Promise.reject(error)
  }
)

// Função para obter headers com autenticação
const getAuthHeaders = () => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    return {
      'Authorization': `Bearer ${token}`
    }
  }
  return {}
}

// Configuração para as APIs geradas
export const apiConfig = new Configuration({
  basePath: baseURL,
  // Função para obter o token dinamicamente
  accessToken: () => {
    const token = localStorage.getItem('accessToken')
    console.log('🔑 [API Config] Getting access token:', {
      hasToken: !!token,
      tokenPreview: token ? `${token.substring(0, 20)}...` : 'No token'
    })
    return token || ''
  },
  // Base options com headers de autenticação dinâmicos
  baseOptions: {
    get headers() {
      return getAuthHeaders()
    }
  }
}) 