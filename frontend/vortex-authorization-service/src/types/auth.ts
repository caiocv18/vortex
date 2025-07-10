export interface LoginRequest {
  identifier: string;
  password: string;
  rememberMe?: boolean;
}

export interface RegisterRequest {
  email: string;
  username: string;
  password: string;
  confirmPassword: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  password: string;
  confirmPassword: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface User {
  id: string;
  email: string;
  username: string;
  roles: string[];
  lastLogin?: string;
  isActive: boolean;
  isVerified: boolean;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface ApiResponse<T = any> {
  success: boolean;
  message: string;
  data?: T;
  timestamp: string;
}

export interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (request: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  logout: () => void;
  forgotPassword: (request: ForgotPasswordRequest) => Promise<void>;
  resetPassword: (request: ResetPasswordRequest) => Promise<void>;
  refreshToken: () => Promise<boolean>;
}