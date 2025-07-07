import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useAuth } from '../hooks/useAuth';

interface RegisterForm {
  email: string;
  name: string;
  password: string;
  confirmPassword: string;
}

const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const { register: registerUser, loading, isAuthenticated } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<RegisterForm>();

  const password = watch('password');

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard');
    }
  }, [isAuthenticated, navigate]);

  const onSubmit = async (data: RegisterForm) => {
    try {
      setIsSubmitting(true);
      await registerUser(data);
      navigate('/dashboard');
    } catch (error) {
      // Error is handled in useAuth hook
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <h1 className="vortex-title">🏢 VORTEX</h1>
          <p className="vortex-subtitle">Criar Nova Conta</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="login-form">
          <div className="form-group">
            <input
              {...register('name', {
                required: 'Nome é obrigatório',
                minLength: {
                  value: 2,
                  message: 'Nome deve ter pelo menos 2 caracteres'
                },
                maxLength: {
                  value: 100,
                  message: 'Nome deve ter no máximo 100 caracteres'
                }
              })}
              type="text"
              placeholder="Nome completo"
              className={`form-input ${errors.name ? 'error' : ''}`}
              autoComplete="name"
            />
            {errors.name && (
              <span className="form-error">{errors.name.message}</span>
            )}
          </div>

          <div className="form-group">
            <input
              {...register('email', {
                required: 'Email é obrigatório',
                pattern: {
                  value: /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i,
                  message: 'Email inválido'
                }
              })}
              type="email"
              placeholder="Email"
              className={`form-input ${errors.email ? 'error' : ''}`}
              autoComplete="email"
            />
            {errors.email && (
              <span className="form-error">{errors.email.message}</span>
            )}
          </div>

          <div className="form-group">
            <input
              {...register('password', {
                required: 'Senha é obrigatória',
                minLength: {
                  value: 8,
                  message: 'Senha deve ter pelo menos 8 caracteres'
                },
                pattern: {
                  value: /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!?*._-]).*$/,
                  message: 'Senha deve conter pelo menos uma letra minúscula, uma maiúscula, um número e um caractere especial'
                }
              })}
              type="password"
              placeholder="Senha"
              className={`form-input ${errors.password ? 'error' : ''}`}
              autoComplete="new-password"
            />
            {errors.password && (
              <span className="form-error">{errors.password.message}</span>
            )}
          </div>

          <div className="form-group">
            <input
              {...register('confirmPassword', {
                required: 'Confirmação de senha é obrigatória',
                validate: value =>
                  value === password || 'Senhas não coincidem'
              })}
              type="password"
              placeholder="Confirmar senha"
              className={`form-input ${errors.confirmPassword ? 'error' : ''}`}
              autoComplete="new-password"
            />
            {errors.confirmPassword && (
              <span className="form-error">{errors.confirmPassword.message}</span>
            )}
          </div>

          <button 
            type="submit" 
            className="btn-primary"
            disabled={isSubmitting || loading}
          >
            {isSubmitting ? (
              <>
                <span className="loading-spinner" />
                Criando conta...
              </>
            ) : (
              'Criar Conta'
            )}
          </button>
        </form>

        <div className="auth-options">
          <Link to="/login" className="btn-link">
            Já tenho conta
          </Link>
        </div>
      </div>
    </div>
  );
};

export default RegisterPage;