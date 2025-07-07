import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useAuth } from '../hooks/useAuth';
import { authService } from '../services/authService';
import toast from 'react-hot-toast';

interface LoginForm {
  email: string;
  password: string;
}

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { login, loading, isAuthenticated } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>();

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard');
    }
  }, [isAuthenticated, navigate]);

  const onSubmit = async (data: LoginForm) => {
    try {
      setIsSubmitting(true);
      await login(data);
      navigate('/dashboard');
    } catch (error) {
      // Error is handled in useAuth hook
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleOAuthLogin = (provider: string) => {
    const url = authService.getOAuthUrl(provider);
    window.location.href = url;
  };

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <h1 className="vortex-title">🏢 VORTEX</h1>
          <p className="vortex-subtitle">Sistema de Controle de Estoque</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="login-form">
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
                }
              })}
              type="password"
              placeholder="Senha"
              className={`form-input ${errors.password ? 'error' : ''}`}
              autoComplete="current-password"
            />
            {errors.password && (
              <span className="form-error">{errors.password.message}</span>
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
                Entrando...
              </>
            ) : (
              'Entrar'
            )}
          </button>
        </form>

        <div className="auth-options">
          <Link to="/register" className="btn-link">
            Criar conta
          </Link>
          <Link to="/forgot-password" className="btn-link">
            Esqueci minha senha
          </Link>
        </div>

        <div className="divider">
          <span>ou</span>
        </div>

        <div className="oauth-buttons">
          <button 
            onClick={() => handleOAuthLogin('google')}
            className="btn-oauth"
            type="button"
          >
            <span className="oauth-icon">🔍</span>
            Entrar com Google
          </button>
          <button 
            onClick={() => handleOAuthLogin('github')}
            className="btn-oauth"
            type="button"
          >
            <span className="oauth-icon">🐙</span>
            Entrar com GitHub
          </button>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;