import { useState, useEffect } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { Link, useParams, useNavigate } from 'react-router';
import { Eye, EyeOff, Lock, LoaderCircle, CircleCheckBig, CircleX, KeyRound } from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { ResetPasswordRequest } from '@/types/auth';
import { useTheme } from '@/hooks/useTheme';

const schema = yup.object({
  password: yup
    .string()
    .required('Password is required')
    .min(8, 'Password must be at least 8 characters')
    .max(128, 'Password must not exceed 128 characters')
    .matches(/[A-Z]/, 'Password must contain at least one uppercase letter')
    .matches(/[a-z]/, 'Password must contain at least one lowercase letter')
    .matches(/[0-9]/, 'Password must contain at least one number')
    .matches(/[!@#$%^&*()_+\-=[\]{}|;:,.<>?]/, 'Password must contain at least one special character'),
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
  { label: 'One special character', test: (pwd) => /[!@#$%^&*()_+\-=[\]{}|;:,.<>?]/.test(pwd) },
];

export function ResetPasswordPage() {
  // Garantir que o tema seja aplicado
  useTheme();
  
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  // Só vira true quando a API aceitou a nova senha. Não dá para usar o isSubmitSuccessful do
  // react-hook-form: o onSubmit trata o erro (o AuthContext já o mostrou num toast), e com o erro
  // tratado o react-hook-form conta o envio como sucesso.
  const [resetSucceeded, setResetSucceeded] = useState(false);
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();
  const { resetPassword, isLoading } = useAuth();

  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: yupResolver(schema),
    mode: 'onChange',
  });

  // useWatch no lugar de watch(): mesma atualização a cada tecla, sem o aviso de incompatibilidade do React Compiler
  const watchedPassword = useWatch({ control, name: 'password', defaultValue: '' });

  useEffect(() => {
    if (!token) {
      navigate('/login');
    }
  }, [token, navigate]);

  // Volta para o login 3 s depois da troca de senha; o timer morre se a página sair antes
  useEffect(() => {
    if (!resetSucceeded) return;
    const timeoutId = setTimeout(() => {
      navigate('/login');
    }, 3000);
    return () => clearTimeout(timeoutId);
  }, [resetSucceeded, navigate]);

  const onSubmit = async (data: FormData) => {
    if (!token) return;

    try {
      await resetPassword({
        token,
        password: data.password,
        confirmPassword: data.confirmPassword,
      } as ResetPasswordRequest);
    } catch {
      // A API recusou (token expirado ou já usado, por exemplo): o AuthContext mostrou o erro num
      // toast e o formulário fica na tela, sem tela de sucesso e sem redirecionar
      return;
    }
    setResetSucceeded(true);
  };

  const loading = isLoading || isSubmitting;

  if (resetSucceeded) {
    return (
      <div className="min-h-screen bg-linear-to-br from-primary-50 to-primary-100 flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
        <div className="w-full max-w-md">
          <div className="bg-white shadow-lg rounded-lg p-8 text-center">
            <div className="mx-auto w-16 h-16 bg-green-500 rounded-full flex items-center justify-center mb-6">
              <CircleCheckBig className="w-8 h-8 text-white" />
            </div>
            <h1 className="text-2xl font-bold text-gray-900 mb-4">Password Reset Successful</h1>
            <p className="text-gray-600 mb-6">
              Your password has been successfully reset. You can now log in with your new password.
            </p>
            <p className="text-sm text-gray-500 mb-8">
              You will be redirected to the login page in a few seconds...
            </p>
            <Link
              to="/login"
              className="inline-flex items-center px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
            >
              Go to Login
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-linear-to-br from-primary-50 to-primary-100 flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
      <div className="w-full max-w-md">
        <div className="bg-white shadow-lg rounded-lg p-8">
          <div className="text-center mb-8">
            <div className="mx-auto w-16 h-16 bg-primary-500 rounded-full flex items-center justify-center mb-4">
              <KeyRound className="w-8 h-8 text-white" />
            </div>
            <h1 className="text-2xl font-bold text-gray-900">Reset Password</h1>
            <p className="text-gray-600 mt-2">
              Enter your new password below
            </p>
          </div>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-2">
                New Password
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
                    block w-full pl-10 pr-10 py-3 border rounded-lg shadow-xs 
                    placeholder-gray-400 focus:outline-hidden focus:ring-2 focus:ring-primary-500 
                    focus:border-primary-500 transition-colors
                    ${errors.password ? 'border-red-300' : 'border-gray-300'}
                  `}
                  placeholder="Enter your new password"
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
                          <CircleCheckBig className="h-3 w-3 text-green-500 mr-1" />
                        ) : (
                          <CircleX className="h-3 w-3 text-red-500 mr-1" />
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
                Confirm New Password
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
                    block w-full pl-10 pr-10 py-3 border rounded-lg shadow-xs 
                    placeholder-gray-400 focus:outline-hidden focus:ring-2 focus:ring-primary-500 
                    focus:border-primary-500 transition-colors
                    ${errors.confirmPassword ? 'border-red-300' : 'border-gray-300'}
                  `}
                  placeholder="Confirm your new password"
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
                rounded-lg shadow-xs text-sm font-medium text-white 
                bg-primary-600 hover:bg-primary-700 focus:outline-hidden 
                focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 
                disabled:opacity-50 disabled:cursor-not-allowed
                transition-colors
              "
            >
              {loading ? (
                <>
                  <LoaderCircle className="animate-spin -ml-1 mr-3 h-5 w-5" />
                  Resetting password...
                </>
              ) : (
                'Reset Password'
              )}
            </button>

            <div className="text-center">
              <Link
                to="/login"
                className="text-sm text-primary-600 hover:text-primary-500 transition-colors"
              >
                Back to Login
              </Link>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}