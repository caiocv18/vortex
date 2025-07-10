import React, { createContext, useContext, useReducer, useEffect, ReactNode } from 'react';
import { 
  User, 
  LoginRequest, 
  RegisterRequest, 
  ForgotPasswordRequest, 
  ResetPasswordRequest,
  AuthContextType 
} from '@/types/auth';
import { apiService } from '@/services/api';
import toast from 'react-hot-toast';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

type AuthAction =
  | { type: 'AUTH_START' }
  | { type: 'AUTH_SUCCESS'; payload: User }
  | { type: 'AUTH_FAILURE' }
  | { type: 'LOGOUT' }
  | { type: 'SET_LOADING'; payload: boolean };

const initialState: AuthState = {
  user: null,
  isAuthenticated: false,
  isLoading: true,
};

function authReducer(state: AuthState, action: AuthAction): AuthState {
  switch (action.type) {
    case 'AUTH_START':
      return { ...state, isLoading: true };
    case 'AUTH_SUCCESS':
      return {
        ...state,
        user: action.payload,
        isAuthenticated: true,
        isLoading: false,
      };
    case 'AUTH_FAILURE':
      return {
        ...state,
        user: null,
        isAuthenticated: false,
        isLoading: false,
      };
    case 'LOGOUT':
      return {
        ...state,
        user: null,
        isAuthenticated: false,
        isLoading: false,
      };
    case 'SET_LOADING':
      return { ...state, isLoading: action.payload };
    default:
      return state;
  }
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [state, dispatch] = useReducer(authReducer, initialState);

  // Check for existing token on app start
  useEffect(() => {
    const token = apiService.getAuthToken();
    if (token && !apiService.isTokenExpired(token)) {
      // Try to refresh token to get user data
      refreshToken();
    } else {
      dispatch({ type: 'AUTH_FAILURE' });
    }
  }, []);

  const login = async (request: LoginRequest): Promise<void> => {
    try {
      dispatch({ type: 'AUTH_START' });
      
      const response = await apiService.login(request);
      const { accessToken, refreshToken: refToken, user } = response.data?.data!;

      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refToken);
      
      dispatch({ type: 'AUTH_SUCCESS', payload: user });
      
      toast.success('Login successful!');
      
      // Redirect to main application with auth data
      const returnUrl = sessionStorage.getItem('vortex_return_url') || 'http://localhost:5173';
      const authData = encodeURIComponent(JSON.stringify({
        accessToken,
        refreshToken: refToken,
        user
      }));
      
      window.location.href = `${returnUrl}?authData=${authData}`;
    } catch (error: any) {
      dispatch({ type: 'AUTH_FAILURE' });
      
      const message = error.response?.data?.message || 'Login failed. Please try again.';
      toast.error(message);
      
      throw error;
    }
  };

  const register = async (request: RegisterRequest): Promise<void> => {
    try {
      dispatch({ type: 'AUTH_START' });
      
      const response = await apiService.register(request);
      const { accessToken, refreshToken: refToken, user } = response.data?.data!;

      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refToken);
      
      dispatch({ type: 'AUTH_SUCCESS', payload: user });
      
      toast.success('Registration successful!');
      
      // Redirect to main application with auth data
      const returnUrl = sessionStorage.getItem('vortex_return_url') || 'http://localhost:5173';
      const authData = encodeURIComponent(JSON.stringify({
        accessToken,
        refreshToken: refToken,
        user
      }));
      
      window.location.href = `${returnUrl}?authData=${authData}`;
    } catch (error: any) {
      dispatch({ type: 'AUTH_FAILURE' });
      
      const message = error.response?.data?.message || 'Registration failed. Please try again.';
      toast.error(message);
      
      throw error;
    }
  };

  const logout = async (): Promise<void> => {
    try {
      const refreshToken = localStorage.getItem('refreshToken');
      if (refreshToken) {
        await apiService.logout({ refreshToken });
      }
    } catch (error) {
      console.error('Logout error:', error);
    } finally {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      dispatch({ type: 'LOGOUT' });
      toast.success('Logged out successfully');
    }
  };

  const forgotPassword = async (request: ForgotPasswordRequest): Promise<void> => {
    try {
      await apiService.forgotPassword(request);
      toast.success('Password reset instructions have been sent to your email');
    } catch (error: any) {
      const message = error.response?.data?.message || 'Failed to send reset email';
      toast.error(message);
      throw error;
    }
  };

  const resetPassword = async (request: ResetPasswordRequest): Promise<void> => {
    try {
      await apiService.resetPassword(request);
      toast.success('Password reset successful! You can now login with your new password.');
    } catch (error: any) {
      const message = error.response?.data?.message || 'Password reset failed';
      toast.error(message);
      throw error;
    }
  };

  const refreshToken = async (): Promise<boolean> => {
    try {
      const refreshToken = localStorage.getItem('refreshToken');
      if (!refreshToken) {
        dispatch({ type: 'AUTH_FAILURE' });
        return false;
      }

      const response = await apiService.refreshToken({ refreshToken });
      const { accessToken, user } = response.data?.data!;

      localStorage.setItem('accessToken', accessToken);
      dispatch({ type: 'AUTH_SUCCESS', payload: user });
      
      return true;
    } catch (error) {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      dispatch({ type: 'AUTH_FAILURE' });
      return false;
    }
  };

  const value: AuthContextType = {
    user: state.user,
    isAuthenticated: state.isAuthenticated,
    isLoading: state.isLoading,
    login,
    register,
    logout,
    forgotPassword,
    resetPassword,
    refreshToken,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}