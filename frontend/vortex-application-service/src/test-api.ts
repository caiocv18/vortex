// Test script to verify API authentication
import axios from 'axios'
import { tiposProdutoApi } from './api'

declare global {
  interface Window {
    testApiAuth?: typeof testApiAuth
  }
}

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
  } catch (error: unknown) {
    const axiosError = axios.isAxiosError(error) ? error : undefined
    console.error('❌ API call failed:', {
      message: error instanceof Error ? error.message : String(error),
      status: axiosError?.response?.status,
      statusText: axiosError?.response?.statusText,
      data: axiosError?.response?.data,
      headers: axiosError?.config?.headers
    })
    return { success: false, error }
  }
}

// Make function available globally for testing
window.testApiAuth = testApiAuth