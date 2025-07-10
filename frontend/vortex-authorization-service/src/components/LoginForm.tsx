import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { Link } from 'react-router-dom';
import { Eye, EyeOff, Lock, Mail, Loader2 } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { LoginRequest } from '@/types/auth';

const schema = yup.object({
  identifier: yup
    .string()
    .required('Email or username is required')
    .min(3, 'Must be at least 3 characters'),
  password: yup
    .string()
    .required('Password is required')
    .min(1, 'Password is required'),
  rememberMe: yup.boolean(),
});

type FormData = yup.InferType<typeof schema>;

export function LoginForm() {
  const [showPassword, setShowPassword] = useState(false);
  const { login, isLoading, forceReset } = useAuth();

  // Reset form state when component mounts (important for post-logout state)
  useEffect(() => {
    console.log('[LoginForm] Component mounted, isLoading:', isLoading);
    
    // Clear any stale tokens on mount to ensure clean state
    const hasStaleTokens = localStorage.getItem('accessToken') || localStorage.getItem('refreshToken');
    if (hasStaleTokens) {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      console.log('[LoginForm] Cleared stale tokens on mount');
    }
    
    // Force reset loading state after a short delay if it's stuck
    const timeoutId = setTimeout(() => {
      if (isLoading) {
        console.log('[LoginForm] Forcing loading state reset after timeout');
        forceReset();
      }
    }, 2000);
    
    return () => clearTimeout(timeoutId);
  }, [isLoading]);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: yupResolver(schema),
    defaultValues: {
      rememberMe: false,
    },
  });

  const onSubmit = async (data: FormData) => {
    try {
      console.log('[LoginForm] Starting login process');
      await login(data as LoginRequest);
    } catch (error) {
      console.error('[LoginForm] Login failed:', error);
      // Error handling is done in the AuthContext
    }
  };

  const loading = isLoading || isSubmitting;
  
  // Debug logging
  console.log('[LoginForm] Render - isLoading:', isLoading, 'isSubmitting:', isSubmitting, 'loading:', loading);

  return (
    <div className="w-full max-w-md mx-auto">
      <div className="bg-white shadow-lg rounded-lg p-8">
        <div className="text-center mb-8">
          <div className="mx-auto w-16 h-16 bg-primary-500 rounded-full flex items-center justify-center mb-4">
            <Lock className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-2xl font-bold text-gray-900">Welcome Back</h1>
          <p className="text-gray-600 mt-2">Sign in to your Vortex account</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
          <div>
            <label htmlFor="identifier" className="block text-sm font-medium text-gray-700 mb-2">
              Email or Username
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <Mail className="h-5 w-5 text-gray-400" />
              </div>
              <input
                id="identifier"
                type="text"
                autoComplete="username"
                {...register('identifier')}
                className={`
                  block w-full pl-10 pr-3 py-3 border rounded-lg shadow-sm 
                  placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary-500 
                  focus:border-primary-500 transition-colors
                  ${errors.identifier ? 'border-red-300' : 'border-gray-300'}
                `}
                placeholder="Enter your email or username"
                disabled={loading}
              />
            </div>
            {errors.identifier && (
              <p className="mt-1 text-sm text-red-600">{errors.identifier.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-2">
              Password
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <Lock className="h-5 w-5 text-gray-400" />
              </div>
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                {...register('password')}
                className={`
                  block w-full pl-10 pr-10 py-3 border rounded-lg shadow-sm 
                  placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary-500 
                  focus:border-primary-500 transition-colors
                  ${errors.password ? 'border-red-300' : 'border-gray-300'}
                `}
                placeholder="Enter your password"
                disabled={loading}
              />
              <button
                type="button"
                className="absolute inset-y-0 right-0 pr-3 flex items-center"
                onClick={() => setShowPassword(!showPassword)}
                disabled={loading}
              >
                {showPassword ? (
                  <EyeOff className="h-5 w-5 text-gray-400 hover:text-gray-600" />
                ) : (
                  <Eye className="h-5 w-5 text-gray-400 hover:text-gray-600" />
                )}
              </button>
            </div>
            {errors.password && (
              <p className="mt-1 text-sm text-red-600">{errors.password.message}</p>
            )}
          </div>

          <div className="flex items-center justify-between">
            <div className="flex items-center">
              <input
                id="rememberMe"
                type="checkbox"
                {...register('rememberMe')}
                className="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
                disabled={loading}
              />
              <label htmlFor="rememberMe" className="ml-2 block text-sm text-gray-700">
                Remember me
              </label>
            </div>

            <Link
              to="/forgot-password"
              className="text-sm text-primary-600 hover:text-primary-500 transition-colors"
            >
              Forgot password?
            </Link>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="
              w-full flex justify-center py-3 px-4 border border-transparent 
              rounded-lg shadow-sm text-sm font-medium text-white 
              bg-primary-600 hover:bg-primary-700 focus:outline-none 
              focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 
              disabled:opacity-50 disabled:cursor-not-allowed
              transition-colors
            "
          >
            {loading ? (
              <>
                <Loader2 className="animate-spin -ml-1 mr-3 h-5 w-5" />
                Signing in...
              </>
            ) : (
              'Sign In'
            )}
          </button>

          <div className="text-center space-y-2">
            <p className="text-sm text-gray-600">
              Don't have an account?{' '}
              <Link
                to="/register"
                className="font-medium text-primary-600 hover:text-primary-500 transition-colors"
              >
                Create one here
              </Link>
            </p>
            
            {/* Emergency reset button - only show when loading is stuck */}
            {loading && (
              <button
                type="button"
                onClick={() => {
                  console.log('[LoginForm] Manual reset triggered');
                  forceReset();
                }}
                className="text-xs text-gray-500 hover:text-gray-700 underline"
              >
                Reset form (if stuck)
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  );
}