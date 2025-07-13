import React from 'react';
import { LoginForm } from '@/components/LoginForm';
import { useTheme } from '@/hooks/useTheme';

export function LoginPage() {
  // Garantir que o tema seja aplicado
  useTheme();
  return (
    <div className="min-h-screen bg-gradient-to-br from-primary-50 to-primary-100 flex flex-col lg:flex-row">
      {/* Left side - Branding */}
      <div className="lg:flex-1 flex flex-col justify-center px-4 sm:px-6 lg:px-20 xl:px-24">
        <div className="mx-auto w-full max-w-md lg:max-w-none">
          <div className="text-center lg:text-left">
            <h1 className="text-4xl font-bold text-gray-900 sm:text-5xl lg:text-6xl">
              <span className="text-primary-600">Vortex</span>
            </h1>
            <p className="mt-4 text-lg text-gray-600 lg:text-xl">
              Sistema de Controle de Estoque
            </p>
            <p className="mt-6 text-base text-gray-500 lg:text-lg">
              Gerencie seu inventário com eficiência e precisão. 
              Tenha controle total sobre seus produtos, movimentações e relatórios.
            </p>
          </div>

          {/* Features showcase */}
          <div className="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
            <div className="flex items-start">
              <div className="flex-shrink-0">
                <div className="flex items-center justify-center h-6 w-6 rounded-md bg-primary-500 text-white">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                </div>
              </div>
              <div className="ml-3">
                <h3 className="text-sm font-medium text-gray-900">Controle em Tempo Real</h3>
                <p className="text-sm text-gray-500">Monitore seu estoque instantaneamente</p>
              </div>
            </div>

            <div className="flex items-start">
              <div className="flex-shrink-0">
                <div className="flex items-center justify-center h-6 w-6 rounded-md bg-primary-500 text-white">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                  </svg>
                </div>
              </div>
              <div className="ml-3">
                <h3 className="text-sm font-medium text-gray-900">Relatórios Avançados</h3>
                <p className="text-sm text-gray-500">Análises detalhadas e insights</p>
              </div>
            </div>

            <div className="flex items-start">
              <div className="flex-shrink-0">
                <div className="flex items-center justify-center h-6 w-6 rounded-md bg-primary-500 text-white">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                  </svg>
                </div>
              </div>
              <div className="ml-3">
                <h3 className="text-sm font-medium text-gray-900">Segurança Total</h3>
                <p className="text-sm text-gray-500">Seus dados protegidos e seguros</p>
              </div>
            </div>

            <div className="flex items-start">
              <div className="flex-shrink-0">
                <div className="flex items-center justify-center h-6 w-6 rounded-md bg-primary-500 text-white">
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                </div>
              </div>
              <div className="ml-3">
                <h3 className="text-sm font-medium text-gray-900">Alta Performance</h3>
                <p className="text-sm text-gray-500">Sistema rápido e responsivo</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Right side - Login form */}
      <div className="lg:flex-1 flex flex-col justify-center py-12 px-4 sm:px-6 lg:px-20 xl:px-24">
        <LoginForm />
      </div>
    </div>
  );
}