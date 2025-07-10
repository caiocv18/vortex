import React from 'react';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { Link } from 'react-router-dom';
import { Mail, ArrowLeft, Loader2, KeyRound } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { ForgotPasswordRequest } from '@/types/auth';

const schema = yup.object({
  email: yup
    .string()
    .required('Email is required')
    .email('Must be a valid email'),
});

type FormData = yup.InferType<typeof schema>;

export function ForgotPasswordPage() {
  const { forgotPassword, isLoading } = useAuth();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isSubmitSuccessful },
  } = useForm<FormData>({
    resolver: yupResolver(schema),
  });

  const onSubmit = async (data: FormData) => {
    try {
      await forgotPassword(data as ForgotPasswordRequest);
    } catch (error) {
      // Error handling is done in the AuthContext
    }
  };

  const loading = isLoading || isSubmitting;

  if (isSubmitSuccessful) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
        <div className="w-full max-w-md">
          <div className="bg-white shadow-lg rounded-lg p-8 text-center">
            <div className="mx-auto w-16 h-16 bg-green-500 rounded-full flex items-center justify-center mb-6">
              <Mail className="w-8 h-8 text-white" />
            </div>
            <h1 className="text-2xl font-bold text-gray-900 mb-4">Check Your Email</h1>
            <p className="text-gray-600 mb-6">
              We've sent password reset instructions to your email address. 
              Please check your inbox and follow the link to reset your password.
            </p>
            <p className="text-sm text-gray-500 mb-8">
              If you don't see the email, check your spam folder or try again.
            </p>
            <Link
              to="/login"
              className="inline-flex items-center text-primary-600 hover:text-primary-500 transition-colors"
            >
              <ArrowLeft className="w-4 h-4 mr-2" />
              Back to Login
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
      <div className="w-full max-w-md">
        <div className="bg-white shadow-lg rounded-lg p-8">
          <div className="text-center mb-8">
            <div className="mx-auto w-16 h-16 bg-primary-500 rounded-full flex items-center justify-center mb-4">
              <KeyRound className="w-8 h-8 text-white" />
            </div>
            <h1 className="text-2xl font-bold text-gray-900">Forgot Password</h1>
            <p className="text-gray-600 mt-2">
              Enter your email address and we'll send you a link to reset your password
            </p>
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
                    placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary-500 
                    focus:border-primary-500 transition-colors
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
                  Sending email...
                </>
              ) : (
                'Send Reset Link'
              )}
            </button>

            <div className="text-center">
              <Link
                to="/login"
                className="inline-flex items-center text-sm text-primary-600 hover:text-primary-500 transition-colors"
              >
                <ArrowLeft className="w-4 h-4 mr-1" />
                Back to Login
              </Link>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}