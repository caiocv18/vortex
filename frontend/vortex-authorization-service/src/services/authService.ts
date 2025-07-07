import apiClient from './apiClient';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  name: string;
  password: string;
  confirmPassword: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
  confirmPassword: string;
}

export interface User {
  id: number;
  email: string;
  name: string;
  provider: string;
  roles: string[];
  lastLogin: string;
  emailVerified: boolean;
}

export interface LoginResponse {
  token: string;
  user: User;
  tokenType: string;
  expiresIn: number;
}

class AuthService {
  async login(credentials: LoginRequest): Promise<LoginResponse> {
    const response = await apiClient.post('/auth/login', credentials);
    return response.data;
  }

  async register(userData: RegisterRequest): Promise<LoginResponse> {
    const response = await apiClient.post('/auth/register', userData);
    return response.data;
  }

  async forgotPassword(email: ForgotPasswordRequest): Promise<void> {
    await apiClient.post('/auth/forgot-password', email);
  }

  async resetPassword(resetData: ResetPasswordRequest): Promise<void> {
    await apiClient.post('/auth/reset-password', resetData);
  }

  async healthCheck(): Promise<any> {
    const response = await apiClient.get('/auth/health');
    return response.data;
  }

  getOAuthUrl(provider: string): string {
    return `${apiClient.defaults.baseURL}/auth/login/${provider}`;
  }

  logout(): void {
    localStorage.removeItem('vortex_auth_token');
    localStorage.removeItem('vortex_user');
  }

  isAuthenticated(): boolean {
    const token = localStorage.getItem('vortex_auth_token');
    return !!token;
  }

  getCurrentUser(): User | null {
    const userStr = localStorage.getItem('vortex_user');
    return userStr ? JSON.parse(userStr) : null;
  }

  saveAuthData(loginResponse: LoginResponse): void {
    localStorage.setItem('vortex_auth_token', loginResponse.token);
    localStorage.setItem('vortex_user', JSON.stringify(loginResponse.user));
  }
}

export const authService = new AuthService();
export default authService;