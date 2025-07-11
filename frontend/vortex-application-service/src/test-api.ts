// Test script to verify API authentication
import { tiposProdutoApi } from './api'

export async function testApiAuth() {
  console.log('🧪 Testing API Authentication...')
  
  // Test 1: Check if token exists
  const token = localStorage.getItem('accessToken')
  console.log('📌 Token check:', {
    hasToken: !!token,
    tokenLength: token?.length || 0,
    tokenPreview: token ? `${token.substring(0, 30)}...` : 'No token'
  })
  
  // Test 2: Try to fetch data
  try {
    console.log('📡 Attempting to fetch tipos-produto...')
    const response = await tiposProdutoApi.findAllTiposProduto()
    console.log('✅ Success! Data received:', response.data)
    return { success: true, data: response.data }
  } catch (error: any) {
    console.error('❌ API call failed:', {
      message: error.message,
      status: error.response?.status,
      statusText: error.response?.statusText,
      data: error.response?.data,
      headers: error.config?.headers
    })
    return { success: false, error }
  }
}

// Make function available globally for testing
(window as any).testApiAuth = testApiAuth