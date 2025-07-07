import React, { useState, useEffect } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { authService } from '../services/authService';
import toast from 'react-hot-toast';

interface ResetPasswordForm {
  newPassword: string;
  confirmPassword: string;
}

const ResetPasswordPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const token = searchParams.get('token');

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<ResetPasswordForm>();

  const newPassword = watch('newPassword');

  useEffect(() => {
    if (!token) {
      toast.error('Token de recuperação inválido');
      navigate('/login');
    }
  }, [token, navigate]);

  const onSubmit = async (data: ResetPasswordForm) => {
    if (!token) return;

    try {
      setIsSubmitting(true);
      await authService.resetPassword({
        token,
        newPassword: data.newPassword,
        confirmPassword: data.confirmPassword,
      });
      setIsSuccess(true);
      toast.success('Senha redefinida com sucesso!');
    } catch (error: any) {
      const message = error.response?.data?.message || 'Erro ao redefinir senha';
      toast.error(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isSuccess) {
    return (
      <div className="login-container">
        <div className="login-card">
          <div className="login-header">
            <h1 className="vortex-title">✅ Sucesso!</h1>
            <p className="vortex-subtitle">Senha redefinida com sucesso</p>
          </div>

          <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
            <p style={{ marginBottom: '1rem', color: 'var(--color-text)' }}>
              Sua senha foi redefinida com sucesso. Agora você pode fazer login com sua nova senha.
            </p>
          </div>

          <div className="auth-options" style={{ justifyContent: 'center' }}>
            <Link to="/login" className="btn-primary">
              Fazer Login
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
          <p className="vortex-subtitle">Redefinir Senha</p>
        </div>

        <div style={{ marginBottom: '1.5rem', textAlign: 'center' }}>
          <p style={{ color: 'var(--color-text)', fontSize: '0.9rem' }}>
            Digite sua nova senha abaixo
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="login-form">
          <div className="form-group">
            <input
              {...register('newPassword', {
                required: 'Nova senha é obrigatória',
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
              placeholder="Nova senha"
              className={`form-input ${errors.newPassword ? 'error' : ''}`}
              autoComplete="new-password"
            />
            {errors.newPassword && (
              <span className="form-error">{errors.newPassword.message}</span>
            )}
          </div>

          <div className="form-group">
            <input
              {...register('confirmPassword', {
                required: 'Confirmação de senha é obrigatória',
                validate: value =>
                  value === newPassword || 'Senhas não coincidem'
              })}
              type="password"
              placeholder="Confirmar nova senha"
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
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <span className="loading-spinner" />
                Redefinindo...
              </>
            ) : (
              'Redefinir Senha'
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

export default ResetPasswordPage;