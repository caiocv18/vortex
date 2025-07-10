import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { Link } from 'react-router-dom';
import { Eye, EyeOff, Lock, Mail, User, Loader2, CheckCircle, XCircle } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { RegisterRequest } from '@/types/auth';

const schema = yup.object({
  email: yup
    .string()
    .required('Email is required')
    .email('Must be a valid email'),
  username: yup
    .string()
    .required('Username is required')
    .min(3, 'Username must be at least 3 characters')
    .max(100, 'Username must not exceed 100 characters')
    .matches(/^[a-zA-Z0-9_-]+$/, 'Username can only contain letters, numbers, underscores, and hyphens'),
  password: yup
    .string()
    .required('Password is required')
    .min(8, 'Password must be at least 8 characters')
    .max(128, 'Password must not exceed 128 characters')
    .matches(/[A-Z]/, 'Password must contain at least one uppercase letter')
    .matches(/[a-z]/, 'Password must contain at least one lowercase letter')
    .matches(/[0-9]/, 'Password must contain at least one number')
    .matches(/[!@#$%^&*()_+\-=\[\]{}|;:,.<>?]/, 'Password must contain at least one special character'),
  confirmPassword: yup
    .string()
    .required('Please confirm your password')
    .oneOf([yup.ref('password')], 'Passwords must match'),
});

type FormData = yup.InferType<typeof schema>;

interface PasswordRequirement {
  label: string;
  test: (password: string) => boolean;
}

const passwordRequirements: PasswordRequirement[] = [
  { label: 'At least 8 characters', test: (pwd) => pwd.length >= 8 },
  { label: 'One uppercase letter', test: (pwd) => /[A-Z]/.test(pwd) },
  { label: 'One lowercase letter', test: (pwd) => /[a-z]/.test(pwd) },
  { label: 'One number', test: (pwd) => /[0-9]/.test(pwd) },
  { label: 'One special character', test: (pwd) => /[!@#$%^&*()_+\-=\[\]{}|;:,.<>?]/.test(pwd) },
];

export function RegisterForm() {
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const { register: registerUser, isLoading } = useAuth();

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: yupResolver(schema),
    mode: 'onChange',
  });

  const watchedPassword = watch('password', '');

  const onSubmit = async (data: FormData) => {
    try {
      await registerUser(data as RegisterRequest);
    } catch (error) {
      // Error handling is done in the AuthContext
    }
  };

  const loading = isLoading || isSubmitting;

  return (
    <div className="w-full max-w-md mx-auto">
      <div className="bg-white shadow-lg rounded-lg p-8">
        <div className="text-center mb-8">
          <div className="mx-auto w-16 h-16 bg-secondary-500 rounded-full flex items-center justify-center mb-4">
            <User className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-2xl font-bold text-gray-900">Create Account</h1>
          <p className="text-gray-600 mt-2">Join the Vortex platform</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-2">
              Email Address
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <Mail className="h-5 w-5 text-gray-400" />
              </div>
              <input
                id="email"
                type="email"
                autoComplete="email"
                {...register('email')}
                className={`
                  block w-full pl-10 pr-3 py-3 border rounded-lg shadow-sm 
                  placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-secondary-500 
                  focus:border-secondary-500 transition-colors
                  ${errors.email ? 'border-red-300' : 'border-gray-300'}
                `}
                placeholder="Enter your email address"
                disabled={loading}
              />
            </div>
            {errors.email && (
              <p className="mt-1 text-sm text-red-600">{errors.email.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="username" className="block text-sm font-medium text-gray-700 mb-2">
              Username
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <User className="h-5 w-5 text-gray-400" />
              </div>
              <input
                id="username"
                type="text"
                autoComplete="username"
                {...register('username')}
                className={`
                  block w-full pl-10 pr-3 py-3 border rounded-lg shadow-sm 
                  placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-secondary-500 
                  focus:border-secondary-500 transition-colors
                  ${errors.username ? 'border-red-300' : 'border-gray-300'}
                `}
                placeholder="Choose a username"
                disabled={loading}
              />
            </div>
            {errors.username && (
              <p className="mt-1 text-sm text-red-600">{errors.username.message}</p>
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
                autoComplete="new-password"
                {...register('password')}
                className={`
                  block w-full pl-10 pr-10 py-3 border rounded-lg shadow-sm 
                  placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-secondary-500 
                  focus:border-secondary-500 transition-colors
                  ${errors.password ? 'border-red-300' : 'border-gray-300'}
                `}
                placeholder="Create a strong password"
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
            
            {/* Password requirements */}
            {watchedPassword && (
              <div className="mt-2 space-y-1">
                {passwordRequirements.map((req, index) => {
                  const isValid = req.test(watchedPassword);
                  return (
                    <div key={index} className="flex items-center text-xs">
                      {isValid ? (
                        <CheckCircle className="h-3 w-3 text-green-500 mr-1" />
                      ) : (
                        <XCircle className="h-3 w-3 text-red-500 mr-1" />
                      )}
                      <span className={isValid ? 'text-green-600' : 'text-red-600'}>
                        {req.label}
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
            
            {errors.password && (
              <p className="mt-1 text-sm text-red-600">{errors.password.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="confirmPassword" className="block text-sm font-medium text-gray-700 mb-2">
              Confirm Password
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <Lock className="h-5 w-5 text-gray-400" />
              </div>
              <input
                id="confirmPassword"
                type={showConfirmPassword ? 'text' : 'password'}
                autoComplete="new-password"
                {...register('confirmPassword')}
                className={`
                  block w-full pl-10 pr-10 py-3 border rounded-lg shadow-sm 
                  placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-secondary-500 
                  focus:border-secondary-500 transition-colors
                  ${errors.confirmPassword ? 'border-red-300' : 'border-gray-300'}
                `}
                placeholder="Confirm your password"
                disabled={loading}
              />
              <button
                type="button"
                className="absolute inset-y-0 right-0 pr-3 flex items-center"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                disabled={loading}
              >
                {showConfirmPassword ? (
                  <EyeOff className="h-5 w-5 text-gray-400 hover:text-gray-600" />
                ) : (
                  <Eye className="h-5 w-5 text-gray-400 hover:text-gray-600" />
                )}
              </button>
            </div>
            {errors.confirmPassword && (
              <p className="mt-1 text-sm text-red-600">{errors.confirmPassword.message}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={loading}
            className="
              w-full flex justify-center py-3 px-4 border border-transparent 
              rounded-lg shadow-sm text-sm font-medium text-white 
              bg-secondary-600 hover:bg-secondary-700 focus:outline-none 
              focus:ring-2 focus:ring-offset-2 focus:ring-secondary-500 
              disabled:opacity-50 disabled:cursor-not-allowed
              transition-colors
            "
          >
            {loading ? (
              <>
                <Loader2 className="animate-spin -ml-1 mr-3 h-5 w-5" />
                Creating account...
              </>
            ) : (
              'Create Account'
            )}
          </button>

          <div className="text-center">
            <p className="text-sm text-gray-600">
              Already have an account?{' '}
              <Link
                to="/login"
                className="font-medium text-secondary-600 hover:text-secondary-500 transition-colors"
              >
                Sign in here
              </Link>
            </p>
          </div>
        </form>
      </div>
    </div>
  );
}