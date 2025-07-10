import axios, { AxiosInstance, AxiosResponse } from 'axios';
import {
  LoginRequest,
  RegisterRequest,
  ForgotPasswordRequest,
  ResetPasswordRequest,
  RefreshTokenRequest,
  LoginResponse,
  ApiResponse
} from '@/types/auth';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081';

class ApiService {
  private api: AxiosInstance;

  constructor() {
    this.api = axios.create({
      baseURL: API_BASE_URL,
      headers: {
        'Content-Type': 'application/json',
      },
      timeout: 10000,
    });

    // Request interceptor to add auth token
    this.api.interceptors.request.use(
      (config) => {
        const token = localStorage.getItem('accessToken');
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      },
      (error) => Promise.reject(error)
    );

    // Response interceptor to handle token refresh
    this.api.interceptors.response.use(
      (response) => response,
      async (error) => {
        const originalRequest = error.config;

        if (error.response?.status === 401 && !originalRequest._retry) {
          originalRequest._retry = true;

          try {
            const refreshToken = localStorage.getItem('refreshToken');
            if (refreshToken) {
              const response = await this.refreshToken({ refreshToken });
              const newToken = response.data?.data?.accessToken;
              
              if (newToken) {
                localStorage.setItem('accessToken', newToken);
              }
              originalRequest.headers.Authorization = `Bearer ${newToken}`;
              
              return this.api(originalRequest);
            }
          } catch (refreshError) {
            // Refresh failed, redirect to login
            localStorage.removeItem('accessToken');
            localStorage.removeItem('refreshToken');
            window.location.href = '/login';
          }
        }

        return Promise.reject(error);
      }
    );
  }

  // Auth endpoints
  async login(request: LoginRequest): Promise<AxiosResponse<ApiResponse<LoginResponse>>> {
    return this.api.post('/api/auth/login', request);
  }

  async register(request: RegisterRequest): Promise<AxiosResponse<ApiResponse<LoginResponse>>> {
    return this.api.post('/api/auth/register', request);
  }

  async refreshToken(request: RefreshTokenRequest): Promise<AxiosResponse<ApiResponse<LoginResponse>>> {
    return this.api.post('/api/auth/refresh', request);
  }

  async logout(request: RefreshTokenRequest): Promise<AxiosResponse<ApiResponse>> {
    return this.api.post('/api/auth/logout', request);
  }

  async forgotPassword(request: ForgotPasswordRequest): Promise<AxiosResponse<ApiResponse>> {
    return this.api.post('/api/auth/forgot-password', request);
  }

  async resetPassword(request: ResetPasswordRequest): Promise<AxiosResponse<ApiResponse>> {
    return this.api.post('/api/auth/reset-password', request);
  }

  // Utility methods
  setAuthToken(token: string) {
    localStorage.setItem('accessToken', token);
  }

  removeAuthToken() {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
  }

  getAuthToken(): string | null {
    return localStorage.getItem('accessToken');
  }

  isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const currentTime = Date.now() / 1000;
      return payload.exp < currentTime;
    } catch {
      return true;
    }
  }
}

export const apiService = new ApiService();
export default apiService;