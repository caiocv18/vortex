import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { authService } from '../services/authService';
import toast from 'react-hot-toast';

interface ForgotPasswordForm {
  email: string;
}

const ForgotPasswordPage: React.FC = () => {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotPasswordForm>();

  const onSubmit = async (data: ForgotPasswordForm) => {
    try {
      setIsSubmitting(true);
      await authService.forgotPassword(data);
      setIsSubmitted(true);
      toast.success('Se o email existir, você receberá instruções para redefinir sua senha');
    } catch (error: any) {
      const message = error.response?.data?.message || 'Erro ao solicitar recuperação de senha';
      toast.error(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isSubmitted) {
    return (
      <div className="login-container">
        <div className="login-card">
          <div className="login-header">
            <h1 className="vortex-title">📧 Email Enviado</h1>
            <p className="vortex-subtitle">Verifique sua caixa de entrada</p>
          </div>

          <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
            <p style={{ marginBottom: '1rem', color: 'var(--color-text)' }}>
              Se o email informado estiver cadastrado em nosso sistema, você receberá instruções para redefinir sua senha.
            </p>
            <p style={{ fontSize: '0.9rem', color: 'var(--color-text)', opacity: 0.8 }}>
              Não esqueça de verificar sua pasta de spam.
            </p>
          </div>

          <div className="auth-options" style={{ justifyContent: 'center' }}>
            <Link to="/login" className="btn-primary">
              Voltar ao Login
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <h1 className="vortex-title">🔐 VORTEX</h1>
          <p className="vortex-subtitle">Recuperar Senha</p>
        </div>

        <div style={{ marginBottom: '1.5rem', textAlign: 'center' }}>
          <p style={{ color: 'var(--color-text)', fontSize: '0.9rem' }}>
            Digite seu email para receber instruções de recuperação de senha
          </p>
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
              placeholder="Digite seu email"
              className={`form-input ${errors.email ? 'error' : ''}`}
              autoComplete="email"
            />
            {errors.email && (
              <span className="form-error">{errors.email.message}</span>
            )}
          </div>

          <button 
            type="submit" 
            className="btn-primary"
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <span className="loading-spinner" />
                Enviando...
              </>
            ) : (
              'Enviar Instruções'
            )}
          </button>
        </form>

        <div className="auth-options" style={{ justifyContent: 'center' }}>
          <Link to="/login" className="btn-link">
            Voltar ao Login
          </Link>
        </div>
      </div>
    </div>
  );
};

export default ForgotPasswordPage;